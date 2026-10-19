package com.dlunaunizar.bobitos.feature.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.SaveFeedbackControls
import com.dlunaunizar.bobitos.core.common.SaveRunner
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.launchPerIdWrite
import com.dlunaunizar.bobitos.core.common.rethrowIfCancelled
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.findByKey
import com.dlunaunizar.bobitos.core.model.findShoppingMatch
import com.dlunaunizar.bobitos.data.repository.IngredientFailure
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepositoryException
import com.dlunaunizar.bobitos.data.repository.PantryRepository
import com.dlunaunizar.bobitos.data.repository.PersonalIngredientRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.data.repository.ShoppingWrite
import com.dlunaunizar.bobitos.data.repository.toIngredientRepositoryException
import com.dlunaunizar.bobitos.feature.common.CATALOG_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingData
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingSource
import com.dlunaunizar.bobitos.feature.common.idsFlow
import com.dlunaunizar.bobitos.feature.common.pantryEntryFor
import com.dlunaunizar.bobitos.feature.ingredients.mergeIngredients
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    private val ingredientRepository: IngredientRepository,
    private val personalIngredients: PersonalIngredientRepository,
    private val ingredientSource: IngredientShoppingSource,
    private val pantryRepository: PantryRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ShoppingUiState())
    val uiState: StateFlow<ShoppingUiState> = mutableUiState.asStateFlow()
    private val saver =
        SaveRunner(viewModelScope, mutableUiState, ShoppingUiState::save, { copy(save = it) }, Throwable::toUiMessage)
    val feedback: SaveFeedbackControls get() = saver
    private var itemsJob: Job? = null
    private var observedSpaceId: String? = null
    private var catalogJob: Job? = null
    private var pantryData: IngredientShoppingData? = null
    private val purchasingIds = mutableSetOf<String>()
    private var pantryLoad: Deferred<IngredientShoppingData>? = null
    private var pantryGeneration = 0

    fun observe(spaceId: String) {
        if (spaceId == observedSpaceId && itemsJob?.isActive == true) return
        itemsJob?.cancel()
        observedSpaceId = spaceId
        mutableUiState.update { it.copy(items = UiState.Loading) }
        itemsJob = viewModelScope.launch {
            repository.items(spaceId)
                .catch {
                    // Sin el texto de la excepción (técnico y en inglés): la pantalla dice que no se ha podido leer la
                    // lista y ofrece «Reintentar».
                    mutableUiState.update { state -> state.copy(items = UiState.Error()) }
                }
                .collect { items ->
                    mutableUiState.update { state ->
                        state.copy(items = UiState.Content(items))
                    }
                }
        }
        // El catálogo para reconocer lo que se marque como comprado: se empieza a leer ya, sin esperar al primer toque.
        if (pantryData == null) startPantryLoad()
    }

    fun stopObserving() {
        itemsJob?.cancel()
        catalogJob?.cancel()
        pantryLoad?.cancel()
        itemsJob = null
        catalogJob = null
        pantryLoad = null
        observedSpaceId = null
    }

    /** Ingredientes, marcas, preferencias y la despensa: solo mientras el editor de ítem está abierto. */
    fun startIngredientAssist() {
        if (catalogJob?.isActive == true) return
        // Sin datos viejos de un ViewModel restaurado: los valores por defecto parten de lo recién leído.
        mutableUiState.update { it.copy(ingredientData = IngredientShoppingData(), pantryIds = emptySet()) }
        val spaceId = observedSpaceId
        catalogJob = viewModelScope.launch {
            // La despensa, para la pista del alta; si no se puede leer, no hay pista.
            if (spaceId != null) {
                launch {
                    pantryRepository.idsFlow(spaceId)
                        .catch { emit(emptySet()) }
                        .collect { ids -> mutableUiState.update { it.copy(pantryIds = ids) } }
                }
            }
            ingredientSource.data().collect { data -> mutableUiState.update { it.copy(ingredientData = data) } }
        }
    }

    /** Suelta los listeners del catálogo y de la despensa al cerrar el editor (conserva lo ya cargado). */
    fun stopIngredientAssist() {
        catalogJob?.cancel()
        catalogJob = null
    }

    /** Crea una ficha de ingrediente a partir del ítem (si aún no existe en el catálogo). */
    fun createIngredientFromItem(item: ShoppingItem) {
        val name = item.name.trim()
        // El resultado dice si se creó: un ingrediente que ya existe no es un fallo, acaba con su propio aviso.
        saver.run(
            noticeOf = { created ->
                if (created) ShoppingUiMessage.IngredientCreated else ShoppingUiMessage.IngredientExists
            },
        ) {
            try {
                // El catálogo solo está cargado con el editor abierto: se carga bajo demanda (comunes y
                // personales). Si no se puede, no se escribe: sin la lista no se puede detectar el duplicado.
                if (findByKey(loadIngredientsOnDemand(), name) != null) throw IngredientAlreadyKnown()
                personalIngredients.create(name, null, null)
                // El ingrediente nuevo tiene que reconocerse al marcar: se descarta lo guardado y se vuelve a leer.
                pantryData = null
                startPantryLoad(restart = true)
                true
            } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
                error.rethrowIfCancelled()
                if (error.isIngredientDuplicate()) false else throw error
            }
        }
    }

    private suspend fun loadIngredientsOnDemand(): List<CatalogIngredient> =
        // Solo el tiempo agotado se cuenta como falta de conexión.
        loadIngredientsWithTimeout() ?: throw IngredientRepositoryException(IngredientFailure.Network)

    private suspend fun loadIngredientsWithTimeout(): List<CatalogIngredient>? = try {
        withTimeoutOrNull(INGREDIENT_LOAD_TIMEOUT_MILLIS) {
            mergeIngredients(ingredientRepository.catalog().first(), personalIngredients.ingredients().first())
        }
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        error.rethrowIfCancelled()
        throw error as? IngredientRepositoryException ?: error.toIngredientRepositoryException()
    }

    /**
     * Alta desde el editor. Si el producto ya está pendiente en la lista, no escribe: deja el aviso en
     * [ShoppingUiState.duplicate] con lo tecleado. Si está comprado, vuelve a apuntar ese artículo (con su nombre y
     * los valores del editor). Si no está, lo añade. Con la lista aún sin cargar, o con su lectura fallida, no
     * escribe nada: sin ella no se pueden buscar repetidos (ver [itemsForAdd]).
     */
    fun addItem(
        spaceId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) {
        if (!validate(name, quantity, notes, brand)) return
        val items = itemsForAdd(spaceId) ?: return
        val match = findShoppingMatch(items, name)
        val normalizedQuantity = quantity.normalized()
        val normalizedNotes = notes.normalized()
        if (match != null && !match.purchased) {
            mutableUiState.update {
                it.copy(
                    duplicate = ShoppingDuplicate(
                        match,
                        normalizedQuantity,
                        normalizedNotes,
                        supermarket,
                        brand.normalized(),
                    ),
                )
            }
            return
        }
        // Solo el artículo nuevo deja `created` (con «Deshacer») en lugar del aviso; reapuntar un comprado conserva el
        // suyo y nunca ofrece deshacer, para no borrar algo que ya existía.
        if (match == null) {
            saver.create(editor = true) {
                val newName = name.trim()
                val id = repository.addItem(spaceId, newName, normalizedQuantity, normalizedNotes, supermarket, brand)
                CreatedItem(id, newName)
            }
        } else {
            saver.run(notice = ShoppingUiMessage.ItemAdded, editor = true) {
                repository.applyWrites(
                    spaceId,
                    listOf(
                        ShoppingWrite.Change(
                            match,
                            normalizedQuantity,
                            normalizedNotes,
                            supermarket,
                            brand,
                            relist = true,
                        ),
                    ),
                )
            }
        }
    }

    /**
     * «Sumar cantidad» en el aviso de repetido: suma la cantidad, rellena con las tecleadas las notas que el artículo
     * tenga vacías y rellena la tienda y la marca como pareja ([ShoppingDuplicate.storeAndBrandAfterSum]). Si la suma
     * no cabe, no hace nada (el aviso lo explica y deshabilita el botón).
     */
    fun sumDuplicate(spaceId: String) {
        val duplicate = mutableUiState.value.duplicate ?: return
        if (duplicate.sumTooLong) return
        mutableUiState.update { it.copy(duplicate = null) }
        val existing = duplicate.existing
        val notes = existing.notes.normalized() ?: duplicate.notes
        val (store, brand) = duplicate.storeAndBrandAfterSum()
        if (!validate(existing.name, duplicate.summed, notes, brand)) return
        saver.run(notice = ShoppingUiMessage.ItemUpdated, editor = true) {
            repository.applyWrites(
                spaceId,
                listOf(
                    ShoppingWrite.Change(
                        seen = existing,
                        quantity = duplicate.summed,
                        notes = notes,
                        supermarket = store,
                        brand = brand,
                        relist = false,
                    ),
                ),
            )
        }
    }

    /** «No añadir» o cerrar el aviso de repetido: no se escribe nada. */
    fun dismissDuplicate() = mutableUiState.update { it.copy(duplicate = null) }

    /** «No añadir»: como [dismissDuplicate] y, además, suelta el estado del editor que se cierra. */
    fun skipDuplicate() {
        dismissDuplicate()
        saver.resetEditor()
    }

    // La lista ya leída. Si aún carga, o si su lectura ha fallado (y entonces se vuelve a escuchar), lo dice en el
    // editor y devuelve null: sin ella no se pueden buscar repetidos.
    private fun itemsForAdd(spaceId: String): List<ShoppingItem>? = when (val items = mutableUiState.value.items) {
        is UiState.Content -> items.value
        UiState.Loading -> null.also { saver.showEditorError(ShoppingUiMessage.ListNotLoaded) }
        is UiState.Error -> null.also {
            saver.showEditorError(ShoppingUiMessage.ListUnavailable)
            observe(spaceId)
        }
    }

    // «Deshacer» el borrado: el artículo vuelve con su id y su estado, al espacio en que estaba. No es un guardado
    // del editor ni lleva aviso propio; si falla, sale el error.
    fun restoreItem(spaceId: String, item: ShoppingItem) = saver.run(force = true) {
        repository.restoreItem(spaceId, item)
    }

    // «Deshacer» el alta: borra justo ese artículo con el borrado de siempre (sin aviso; si falla, sale el error).
    fun undoCreate(id: String) {
        val spaceId = observedSpaceId ?: return
        saver.run(force = true) { repository.deleteItem(spaceId, id) }
    }

    fun updateItem(
        spaceId: String,
        itemId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) {
        if (!validate(name, quantity, notes, brand)) return
        saver.run(notice = ShoppingUiMessage.ItemUpdated, editor = true) {
            repository.updateItem(
                spaceId,
                itemId,
                name.trim(),
                quantity.normalized(),
                notes.normalized(),
                supermarket,
                brand,
            )
        }
    }

    // Marcar y desmarcar no pasan por el `SaveRunner` (no encienden «Guardando…»): cada producto lleva su propia escritura en curso, así que
    // varios seguidos salen todos al momento y no apagan el resto de la pantalla. Devuelve false (sin escribir) si ese
    // producto ya tiene una escritura en curso. [onFinished] recibe cómo acabó.
    fun setPurchased(
        spaceId: String,
        itemId: String,
        purchased: Boolean,
        onFinished: (Boolean) -> Unit = {},
    ): Boolean {
        if (itemId in purchasingIds) return false
        val item = (mutableUiState.value.items as? UiState.Content)?.value?.firstOrNull { it.id == itemId }
        return viewModelScope.launchPerIdWrite(
            purchasingIds,
            itemId,
            onFinished = onFinished,
            onSuccess = {
                val notice = if (purchased) ShoppingUiMessage.ItemMarked else ShoppingUiMessage.ItemUnmarked
                saver.showNotice(notice)
            },
            onFailure = { error -> saver.showError(error.toUiMessage()) },
        ) {
            val entry = if (purchased && item != null) pantryEntryFor(item.name, dataForPantry()) else null
            repository.setPurchased(spaceId, itemId, purchased, entry)
        }
    }

    // Catálogo para reconocer el ingrediente al marcar comprado: el del editor si ya está cargado; si no, el leído en
    // segundo plano. Se espera 5 s como máximo y sin cancelar la lectura, que sigue y sirve a los marcados siguientes;
    // sin catálogo a tiempo, el artículo se marca igual, pero no entra en la despensa.
    private suspend fun dataForPantry(): IngredientShoppingData {
        mutableUiState.value.ingredientData.takeIf { it.ingredients.isNotEmpty() }?.let { return it }
        pantryData?.let { return it }
        return withTimeoutOrNull(CATALOG_TIMEOUT_MILLIS) { startPantryLoad().await() } ?: IngredientShoppingData()
    }

    // Una sola lectura del catálogo a la vez (con el límite largo); [restart] la sustituye aunque haya una en curso.
    private fun startPantryLoad(restart: Boolean = false): Deferred<IngredientShoppingData> {
        pantryLoad?.takeIf { it.isActive && !restart }?.let { return it }
        val generation = ++pantryGeneration
        return viewModelScope.async {
            val data = withTimeoutOrNull(INGREDIENT_LOAD_TIMEOUT_MILLIS) {
                ingredientSource.data().catch { }.firstOrNull()
            } ?: IngredientShoppingData()
            // Una lectura sustituida mientras corría no pisa los datos de la nueva.
            if (data.ingredients.isNotEmpty() && generation == pantryGeneration) pantryData = data
            data
        }.also { pantryLoad = it }
    }

    // onDeleted se llama solo si el borrado termina bien: así el «Deshacer» no se ofrece para un borrado fallido ni
    // se pulsa mientras el borrado sigue en curso. Sin notice: el feedback lo da el Snackbar de la pantalla.
    fun deleteItem(spaceId: String, itemId: String, onDeleted: () -> Unit = {}) {
        saver.run(afterSuccess = { onDeleted() }) {
            repository.deleteItem(spaceId, itemId)
        }
    }

    fun clearPurchased(spaceId: String) {
        saver.run(
            notice = ShoppingUiMessage.PurchasedCleared,
            onSuccess = { count -> copy(lastClearedCount = count) },
        ) { repository.clearPurchased(spaceId) }
    }

    private fun validate(name: String, quantity: String?, notes: String?, brand: String?): Boolean {
        val error = ShoppingValidation.validate(name, quantity, notes, brand) ?: return true
        saver.showEditorError(error)
        return false
    }
}

// Con la caché del catálogo vacía, su primera emisión puede esperar hasta 10 s la metainformación
// (META_TIMEOUT_MILLIS) y después leer del servidor; el margen cubre las dos esperas.
private const val INGREDIENT_LOAD_TIMEOUT_MILLIS = 30_000L

private class IngredientAlreadyKnown : Exception()

private fun Throwable.isIngredientDuplicate() = this is IngredientAlreadyKnown ||
    (this as? IngredientRepositoryException)?.failure == IngredientFailure.AlreadyExists

private fun IngredientFailure?.toShoppingUiMessage() = when (this) {
    IngredientFailure.NameRequired -> ShoppingUiMessage.NameRequired
    IngredientFailure.NameTooLong -> ShoppingUiMessage.NameTooLong
    IngredientFailure.NotAuthenticated -> ShoppingUiMessage.NotAuthenticated
    IngredientFailure.EmailNotVerified -> ShoppingUiMessage.EmailNotVerified
    IngredientFailure.PermissionDenied -> ShoppingUiMessage.PermissionDenied
    IngredientFailure.Network -> ShoppingUiMessage.NetworkError
    else -> ShoppingUiMessage.UnexpectedError
}

private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun Throwable.toUiMessage(): ShoppingUiMessage = if (this is SaveTimeoutException) {
    ShoppingUiMessage.SaveTimeout
} else {
    toRepositoryUiMessage()
}

private fun Throwable.toRepositoryUiMessage(): ShoppingUiMessage {
    if (this is IngredientRepositoryException) return failure.toShoppingUiMessage()
    return toShoppingRepositoryUiMessage()
}

private fun Throwable.toShoppingRepositoryUiMessage() = when ((this as? ShoppingRepositoryException)?.failure) {
    ShoppingFailure.NameRequired -> ShoppingUiMessage.NameRequired
    ShoppingFailure.NameTooLong -> ShoppingUiMessage.NameTooLong
    ShoppingFailure.QuantityTooLong -> ShoppingUiMessage.QuantityTooLong
    ShoppingFailure.NotesTooLong -> ShoppingUiMessage.NotesTooLong
    ShoppingFailure.BrandTooLong -> ShoppingUiMessage.BrandTooLong
    ShoppingFailure.NotAuthenticated -> ShoppingUiMessage.NotAuthenticated
    ShoppingFailure.EmailNotVerified -> ShoppingUiMessage.EmailNotVerified
    ShoppingFailure.SpaceNotFound -> ShoppingUiMessage.SpaceNotFound
    ShoppingFailure.ItemNotFound -> ShoppingUiMessage.ItemNotFound
    ShoppingFailure.ItemChanged -> ShoppingUiMessage.ItemChanged
    ShoppingFailure.PermissionDenied -> ShoppingUiMessage.PermissionDenied
    ShoppingFailure.Network -> ShoppingUiMessage.NetworkError
    ShoppingFailure.Unknown,
    null,
    -> ShoppingUiMessage.UnexpectedError
}
