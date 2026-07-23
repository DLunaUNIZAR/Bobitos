package com.dlunaunizar.bobitos.feature.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.slug
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    private val ingredientRepository: IngredientRepository,
    private val ingredientPrefsRepository: IngredientPrefsRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ShoppingUiState())
    val uiState: StateFlow<ShoppingUiState> = mutableUiState.asStateFlow()
    private var itemsJob: Job? = null
    private var observedSpaceId: String? = null
    private var catalogJob: Job? = null
    private var prefsJob: Job? = null

    fun observe(spaceId: String) {
        observePrefs()
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
        prefsJob?.cancel()
        catalogJob?.cancel()
        itemsJob = null
        prefsJob = null
        catalogJob = null
        observedSpaceId = null
    }

    // Preferencias del usuario: se observan mientras se ve la lista (1 doc), para sugerir super/marca
    // en las tarjetas y prerrellenar el editor.
    private fun observePrefs() {
        if (prefsJob?.isActive == true) return
        prefsJob = viewModelScope.launch {
            ingredientPrefsRepository.prefs()
                .catch { mutableUiState.update { it.copy(ingredientPrefs = emptyMap()) } }
                .collect { prefs -> mutableUiState.update { it.copy(ingredientPrefs = prefs) } }
        }
    }

    /** Catálogo (acotado): solo mientras el editor de ítem está abierto (autocompletado del nombre). */
    fun startIngredientAssist() {
        if (catalogJob?.isActive == true) return
        catalogJob = viewModelScope.launch {
            ingredientRepository.catalog()
                .catch { mutableUiState.update { it.copy(catalog = emptyList()) } }
                .collect { list -> mutableUiState.update { it.copy(catalog = list) } }
        }
    }

    /** Suelta el listener del catálogo al cerrar el editor (conserva lo ya cargado). */
    fun stopIngredientAssist() {
        catalogJob?.cancel()
        catalogJob = null
    }

    /** Aplica al ítem el supermercado/marca de la preferencia del usuario para ese ingrediente. */
    fun applyIngredientPref(spaceId: String, item: ShoppingItem) {
        val pref = mutableUiState.value.ingredientPrefs[slug(item.name)] ?: return
        runAction(ShoppingUiMessage.ItemUpdated) {
            repository.updateItem(spaceId, item.id, item.name, item.quantity, item.notes, pref.supermarket, pref.brand)
        }
    }

    /** Guarda el supermercado/marca del ítem como tu preferencia por defecto para ese ingrediente. */
    fun saveItemAsPreference(item: ShoppingItem) {
        if (item.supermarket == null && item.brand.isNullOrBlank()) return
        runAction(ShoppingUiMessage.PrefSaved) {
            ingredientPrefsRepository.setPref(slug(item.name), item.supermarket, item.brand)
        }
    }

    /** Crea una ficha de ingrediente a partir del ítem (si aún no existe en el catálogo). */
    fun createIngredientFromItem(item: ShoppingItem) {
        if (mutableUiState.value.isSaving) return
        val name = item.name.trim()
        viewModelScope.launch {
            val existing = ingredientRepository.ingredientById(slug(name))
            if (existing != null) {
                showNotice(ShoppingUiMessage.IngredientExists)
                return@launch
            }
            runAction(ShoppingUiMessage.IngredientCreated) {
                ingredientRepository.createIngredient(name, null, null)
            }
        }
    }

    private fun showNotice(message: ShoppingUiMessage) {
        mutableUiState.update {
            it.copy(writeStatus = ShoppingWriteStatus.SAVED, notice = message, error = null)
        }
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
        // Si el usuario no fijó super ni marca, aplicamos su preferencia para ese ingrediente (por slug).
        val pref = if (supermarket == null && brand == null) {
            mutableUiState.value.ingredientPrefs[slug(name)]
        } else {
            null
        }
        runAction(ShoppingUiMessage.ItemAdded) {
            repository.addItem(
                spaceId,
                name.trim(),
                quantity.normalized(),
                notes.normalized(),
                supermarket ?: pref?.supermarket,
                brand ?: pref?.brand,
            )
        }
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
        if (!validate(name, quantity, notes)) return
        runAction(ShoppingUiMessage.ItemUpdated) {
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
        showError(error)
        return false
    }

    private fun showError(message: ShoppingUiMessage) {
        mutableUiState.update {
            it.copy(
                isSaving = false,
                writeStatus = ShoppingWriteStatus.ERROR,
                error = message,
                notice = null,
            )
        }
    }

    private fun runAction(successNotice: ShoppingUiMessage?, action: suspend () -> Unit) {
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
                action()
                mutableUiState.update {
                    it.copy(
                        isSaving = false,
                        writeStatus = ShoppingWriteStatus.SAVED,
                        notice = successNotice,
                    )
                }
            } catch (error: Throwable) {
                showError(error.toUiMessage())
            }
        }
    }
}

private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun Throwable.toUiMessage(): ShoppingUiMessage = when ((this as? ShoppingRepositoryException)?.failure) {
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
