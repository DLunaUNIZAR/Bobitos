package com.dlunaunizar.bobitos.feature.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.failed
import com.dlunaunizar.bobitos.core.common.started
import com.dlunaunizar.bobitos.core.common.succeeded
import com.dlunaunizar.bobitos.core.common.withEditorSaveTimeout
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.findByKey
import com.dlunaunizar.bobitos.core.model.findShoppingMatch
import com.dlunaunizar.bobitos.data.repository.IngredientFailure
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepositoryException
import com.dlunaunizar.bobitos.data.repository.PersonalIngredientRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.data.repository.ShoppingWrite
import com.dlunaunizar.bobitos.data.repository.toIngredientRepositoryException
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingData
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingSource
import com.dlunaunizar.bobitos.feature.ingredients.mergeIngredients
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ShoppingUiState())
    val uiState: StateFlow<ShoppingUiState> = mutableUiState.asStateFlow()
    private var itemsJob: Job? = null
    private var observedSpaceId: String? = null
    private var catalogJob: Job? = null

    fun observe(spaceId: String) {
        if (spaceId == observedSpaceId && itemsJob?.isActive == true) return
        itemsJob?.cancel()
        observedSpaceId = spaceId
        mutableUiState.update { it.copy(items = UiState.Loading) }
        itemsJob = viewModelScope.launch {
            repository.items(spaceId)
                .catch { error ->
                    mutableUiState.update { state ->
                        state.copy(items = UiState.Error(error.message))
                    }
                }
                .collect { items ->
                    mutableUiState.update { state ->
                        state.copy(items = UiState.Content(items))
                    }
                }
        }
    }
    fun stopObserving() {
        itemsJob?.cancel()
        catalogJob?.cancel()
        itemsJob = null
        catalogJob = null
        observedSpaceId = null
    }

    /** Ingredientes, marcas y preferencias: solo mientras el editor de ítem está abierto. */
    fun startIngredientAssist() {
        if (catalogJob?.isActive == true) return
        // Sin datos viejos de un ViewModel restaurado: los valores por defecto parten de lo recién leído.
        mutableUiState.update { it.copy(ingredientData = IngredientShoppingData()) }
        catalogJob = viewModelScope.launch {
            ingredientSource.data().collect { data -> mutableUiState.update { it.copy(ingredientData = data) } }
        }
    }

    /** Suelta el listener del catálogo al cerrar el editor (conserva lo ya cargado). */
    fun stopIngredientAssist() {
        catalogJob?.cancel()
        catalogJob = null
    }

    /** Crea una ficha de ingrediente a partir del ítem (si aún no existe en el catálogo). */
    fun createIngredientFromItem(item: ShoppingItem) {
        val name = item.name.trim()
        runAction(ShoppingUiMessage.IngredientCreated) {
            // El catálogo solo está cargado con el editor abierto: se carga bajo demanda (comunes y
            // personales). Si no se puede, no se escribe: sin la lista no se puede detectar el duplicado.
            if (findByKey(loadIngredientsOnDemand(), name) != null) throw IngredientAlreadyKnown()
            personalIngredients.create(name, null, null)
        }
    }

    private suspend fun loadIngredientsOnDemand(): List<CatalogIngredient> =
        // Solo el tiempo agotado se cuenta como falta de conexión.
        loadIngredientsWithTimeout() ?: throw IngredientRepositoryException(IngredientFailure.Network)

    private suspend fun loadIngredientsWithTimeout(): List<CatalogIngredient>? = try {
        withTimeoutOrNull(INGREDIENT_LOAD_TIMEOUT_MILLIS) {
            mergeIngredients(ingredientRepository.catalog().first(), personalIngredients.ingredients().first())
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
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
        runAction(ShoppingUiMessage.ItemAdded, editor = true) {
            if (match == null) {
                repository.addItem(spaceId, name.trim(), normalizedQuantity, normalizedNotes, supermarket, brand)
            } else {
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
        runAction(ShoppingUiMessage.ItemUpdated, editor = true) {
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

    // La lista ya leída. Si aún carga, o si su lectura ha fallado (y entonces se vuelve a escuchar), lo dice en el
    // editor y devuelve null: sin ella no se pueden buscar repetidos.
    private fun itemsForAdd(spaceId: String): List<ShoppingItem>? = when (val items = mutableUiState.value.items) {
        is UiState.Content -> items.value
        UiState.Loading -> null.also { showError(ShoppingUiMessage.ListNotLoaded, editor = true) }
        is UiState.Error -> null.also {
            showError(ShoppingUiMessage.ListUnavailable, editor = true)
            observe(spaceId)
        }
    }

    // Recrea un ítem borrado («Deshacer»): no es un guardado del editor, no mueve su estado.
    fun restoreItem(spaceId: String, item: ShoppingItem) = runAction(ShoppingUiMessage.ItemAdded) {
        repository.addItem(spaceId, item.name, item.quantity, item.notes, item.supermarket, item.brand)
    }

    fun consumeEditorSave() = mutableUiState.update { it.copy(editorSave = EditorSaveStatus.IDLE) }

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
        runAction(ShoppingUiMessage.ItemUpdated, editor = true) {
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

    fun setPurchased(spaceId: String, itemId: String, purchased: Boolean) {
        runAction(
            if (purchased) ShoppingUiMessage.ItemMarked else ShoppingUiMessage.ItemUnmarked,
        ) {
            repository.setPurchased(spaceId, itemId, purchased)
        }
    }

    fun deleteItem(spaceId: String, itemId: String) {
        // Sin notice: el feedback del borrado (con «Deshacer») lo da el Snackbar de la pantalla.
        runAction(null) {
            repository.deleteItem(spaceId, itemId)
        }
    }

    fun clearPurchased(spaceId: String) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update {
            it.copy(
                isSaving = true,
                writeStatus = ShoppingWriteStatus.SAVING,
                error = null,
                notice = null,
            )
        }
        viewModelScope.launch {
            try {
                val count = repository.clearPurchased(spaceId)
                mutableUiState.update {
                    it.copy(
                        isSaving = false,
                        writeStatus = ShoppingWriteStatus.SAVED,
                        notice = ShoppingUiMessage.PurchasedCleared,
                        lastClearedCount = count,
                    )
                }
            } catch (error: Throwable) {
                showError(error.toUiMessage())
            }
        }
    }

    fun clearFeedback() {
        mutableUiState.update {
            it.copy(
                writeStatus = if (it.isSaving) {
                    ShoppingWriteStatus.SAVING
                } else {
                    ShoppingWriteStatus.IDLE
                },
                error = null,
                notice = null,
            )
        }
    }

    private fun validate(name: String, quantity: String?, notes: String?, brand: String?): Boolean {
        val error = ShoppingValidation.validate(name, quantity, notes, brand) ?: return true
        showError(error, editor = true)
        return false
    }

    private fun showError(message: ShoppingUiMessage, editor: Boolean = false) {
        mutableUiState.update {
            it.copy(
                isSaving = false,
                editorSave = it.editorSave.failed(editor),
                writeStatus = ShoppingWriteStatus.ERROR,
                error = message,
                notice = null,
            )
        }
    }

    private fun runAction(successNotice: ShoppingUiMessage?, editor: Boolean = false, action: suspend () -> Unit) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update {
            it.copy(
                isSaving = true,
                writeStatus = ShoppingWriteStatus.SAVING,
                editorSave = it.editorSave.started(editor),
                error = null,
                notice = null,
            )
        }
        viewModelScope.launch {
            runCatching { withEditorSaveTimeout(editor) { action() } }
                .onSuccess {
                    mutableUiState.update {
                        it.copy(
                            isSaving = false,
                            writeStatus = ShoppingWriteStatus.SAVED,
                            editorSave = it.editorSave.succeeded(editor),
                            notice = successNotice,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (error.isIngredientDuplicate()) {
                        mutableUiState.update {
                            it.copy(
                                isSaving = false,
                                writeStatus = ShoppingWriteStatus.SAVED,
                                notice = ShoppingUiMessage.IngredientExists,
                            )
                        }
                        return@onFailure
                    }
                    showError(error.toUiMessage(), editor)
                }
        }
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
