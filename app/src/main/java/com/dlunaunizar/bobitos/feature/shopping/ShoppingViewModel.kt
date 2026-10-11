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
import com.dlunaunizar.bobitos.data.repository.IngredientFailure
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepositoryException
import com.dlunaunizar.bobitos.data.repository.PersonalIngredientRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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

    /** Catálogo (acotado): solo mientras el editor de ítem está abierto (autocompletado del nombre). */
    fun startIngredientAssist() {
        if (catalogJob?.isActive == true) return
        catalogJob = viewModelScope.launch {
            // Comunes y personales juntos: sirven para sugerir nombres y para detectar duplicados.
            val common = ingredientRepository.catalog().catch { emit(emptyList()) }
            val personal = personalIngredients.ingredients().catch { emit(emptyList()) }
            combine(common, personal) { shared, own -> shared + own }
                .collect { list -> mutableUiState.update { it.copy(catalog = list) } }
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

    private suspend fun loadIngredientsOnDemand(): List<CatalogIngredient> {
        val loaded = try {
            withTimeoutOrNull(INGREDIENT_LOAD_TIMEOUT_MILLIS) {
                ingredientRepository.catalog().first() + personalIngredients.ingredients().first()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            null
        }
        return loaded ?: throw IngredientRepositoryException(IngredientFailure.Network)
    }

    fun addItem(
        spaceId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) {
        if (!validate(name, quantity, notes)) return
        runAction(ShoppingUiMessage.ItemAdded, editor = true) {
            repository.addItem(
                spaceId,
                name.trim(),
                quantity.normalized(),
                notes.normalized(),
                supermarket,
                brand,
            )
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
        if (!validate(name, quantity, notes)) return
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

    private fun validate(name: String, quantity: String?, notes: String?): Boolean {
        val error = ShoppingValidation.validate(name, quantity, notes) ?: return true
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

private const val INGREDIENT_LOAD_TIMEOUT_MILLIS = 10_000L

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
    ShoppingFailure.PermissionDenied -> ShoppingUiMessage.PermissionDenied
    ShoppingFailure.Network -> ShoppingUiMessage.NetworkError
    ShoppingFailure.Unknown,
    null,
    -> ShoppingUiMessage.UnexpectedError
}
