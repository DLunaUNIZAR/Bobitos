package com.dlunaunizar.bobitos.feature.ingredients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.failed
import com.dlunaunizar.bobitos.core.common.started
import com.dlunaunizar.bobitos.core.common.succeeded
import com.dlunaunizar.bobitos.core.common.withEditorSaveTimeout
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.openfoodfacts.OffException
import com.dlunaunizar.bobitos.data.openfoodfacts.OffFailure
import com.dlunaunizar.bobitos.data.openfoodfacts.OpenFoodFactsClient
import com.dlunaunizar.bobitos.data.repository.BrandFailure
import com.dlunaunizar.bobitos.data.repository.BrandRepositoryException
import com.dlunaunizar.bobitos.data.repository.IngredientBrandRepository
import com.dlunaunizar.bobitos.data.repository.IngredientFailure
import com.dlunaunizar.bobitos.data.repository.IngredientPrefFailure
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsException
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepositoryException
import com.dlunaunizar.bobitos.data.repository.PersonalIngredientRepository
import com.dlunaunizar.bobitos.data.repository.sortedForBrands
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IngredientDetailViewModel @Inject constructor(
    private val repository: IngredientRepository,
    private val personalRepository: PersonalIngredientRepository,
    private val prefsRepository: IngredientPrefsRepository,
    private val brandRepository: IngredientBrandRepository,
    private val openFoodFactsClient: OpenFoodFactsClient,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(IngredientDetailUiState())
    val uiState: StateFlow<IngredientDetailUiState> = mutableUiState.asStateFlow()

    private var ingredientId: String = ""
    private var catalogJob: Job? = null
    private var personalJob: Job? = null
    private var brandsJob: Job? = null
    private var prefsJob: Job? = null

    // Comunes y personales por separado; el ingrediente de la ficha es el común y, si no, el personal.
    private var common: List<CatalogIngredient> = emptyList()
    private var personal: List<CatalogIngredient> = emptyList()

    fun observe(id: String) {
        if (id == ingredientId && catalogJob?.isActive == true) return
        ingredientId = id
        mutableUiState.update {
            it.copy(
                ingredientId = id,
                isAdmin = repository.isCurrentUserCatalogAdmin(),
                currentUid = repository.currentUserId(),
            )
        }
        common = emptyList()
        personal = emptyList()
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            repository.catalog()
                .catch { /* el error del catálogo se refleja como ingrediente ausente */ }
                .collect { list ->
                    common = list
                    publishIngredient(id, loaded = true)
                }
        }
        personalJob?.cancel()
        personalJob = viewModelScope.launch {
            personalRepository.ingredients()
                .catch { personal = emptyList() }
                .collect { list ->
                    personal = list
                    publishIngredient(id)
                }
        }
        brandsJob?.cancel()
        brandsJob = viewModelScope.launch {
            brandRepository.brands()
                .map { all -> all.filter { it.ingredientId == id }.sortedForBrands() }
                .catch { mutableUiState.update { it.copy(brands = emptyList()) } }
                .collect { brands -> mutableUiState.update { it.copy(brands = brands) } }
        }
        prefsJob?.cancel()
        prefsJob = viewModelScope.launch {
            prefsRepository.prefs()
                .map { it[id]?.stores.orEmpty() }
                .catch { mutableUiState.update { it.copy(stores = emptyList()) } }
                .collect { stores -> mutableUiState.update { it.copy(stores = stores) } }
        }
    }

    private fun publishIngredient(id: String, loaded: Boolean = false) {
        val found = common.firstOrNull { it.id == id } ?: personal.firstOrNull { it.id == id }
        mutableUiState.update { it.copy(ingredient = found, loaded = it.loaded || loaded) }
    }

    fun stopObserving() {
        listOf(catalogJob, personalJob, brandsJob, prefsJob).forEach { it?.cancel() }
        catalogJob = null
        personalJob = null
        brandsJob = null
        prefsJob = null
        ingredientId = ""
    }

    fun updateIngredient(name: String, category: String?, defaultUnit: String?) {
        if (name.trim().isEmpty()) {
            showError(IngredientUiMessage.NameRequired, editor = true)
            return
        }
        runAction(IngredientUiMessage.Saved, editor = true) {
            if (isPersonal()) {
                personalRepository.update(ingredientId, name.trim(), category, defaultUnit)
            } else {
                repository.updateIngredient(ingredientId, name.trim(), category, defaultUnit)
            }
        }
    }

    fun deleteIngredient() {
        runAction(IngredientUiMessage.Deleted, finishOnSuccess = true) {
            if (isPersonal()) personalRepository.delete(ingredientId) else repository.deleteIngredient(ingredientId)
        }
    }

    private fun isPersonal() = mutableUiState.value.ingredient?.personal == true

    /** Añade o quita [store] de «Dónde lo compro»; la lista guardada conserva el orden de elección. */
    fun toggleStore(store: Supermarket) {
        val stores = toggleStore(mutableUiState.value.stores, store)
        runAction(notice = null) { prefsRepository.setStores(ingredientId, stores) }
    }

    fun setFavorite(brandId: String, favorite: Boolean) {
        runAction(notice = null) { brandRepository.setFavorite(brandId, favorite) }
    }

    /** Alta ([brandId] nulo) o edición de una marca desde el editor. */
    internal fun saveBrand(brandId: String?, draft: BrandDraft) {
        if (draft.name.trim().isEmpty()) {
            showError(IngredientUiMessage.BrandNameRequired, editor = true)
            return
        }
        val input = draft.toInput(ingredientId)
        runAction(IngredientUiMessage.BrandSaved, editor = true) {
            if (brandId == null) brandRepository.addBrand(input) else brandRepository.updateBrand(brandId, input)
        }
    }

    fun deleteBrand(brandId: String) {
        runAction(IngredientUiMessage.BrandDeleted) { brandRepository.deleteBrand(brandId) }
    }

    /** Consulta Open Food Facts por el código escaneado y deja un borrador de marca prerrellenado. */
    fun lookupBarcode(barcode: String) {
        if (mutableUiState.value.isLookingUp) return
        mutableUiState.update { it.copy(isLookingUp = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                val product = openFoodFactsClient.lookup(barcode)
                val draft = ScannedBrand(
                    name = product?.brand ?: product?.productName.orEmpty(),
                    barcode = barcode,
                    nutrition = product?.nutrition,
                )
                mutableUiState.update {
                    it.copy(
                        isLookingUp = false,
                        scannedBrand = draft,
                        notice = if (product == null) IngredientUiMessage.ScanNotFound else null,
                    )
                }
            } catch (error: OffException) {
                mutableUiState.update {
                    it.copy(isLookingUp = false, error = error.failure.toUiMessage())
                }
            }
        }
    }

    fun consumeScannedBrand() {
        mutableUiState.update { it.copy(scannedBrand = null) }
    }

    fun consumeEditorSave() = mutableUiState.update { it.copy(editorSave = EditorSaveStatus.IDLE) }

    fun clearFeedback() {
        mutableUiState.update { it.copy(error = null, notice = null) }
    }

    private fun showError(message: IngredientUiMessage, editor: Boolean = false) {
        mutableUiState.update {
            it.copy(isSaving = false, error = message, notice = null, editorSave = it.editorSave.failed(editor))
        }
    }

    private fun runAction(
        notice: IngredientUiMessage?,
        finishOnSuccess: Boolean = false,
        editor: Boolean = false,
        action: suspend () -> Unit,
    ) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update {
            it.copy(isSaving = true, error = null, notice = null, editorSave = it.editorSave.started(editor))
        }
        viewModelScope.launch {
            runCatching { withEditorSaveTimeout(editor) { action() } }
                .onSuccess {
                    mutableUiState.update {
                        it.copy(
                            isSaving = false,
                            notice = notice,
                            finished = it.finished || finishOnSuccess,
                            editorSave = it.editorSave.succeeded(editor),
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    showError(error.toUiMessage(), editor)
                }
        }
    }
}

// Utilidad para el formulario de nutrición: acepta coma o punto decimal; vacío → null.
internal fun parseNutritionValue(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()

private fun Throwable.toUiMessage(): IngredientUiMessage = when (this) {
    is IngredientRepositoryException -> failure.toUiMessage()
    is BrandRepositoryException -> failure.toUiMessage()
    is IngredientPrefsException -> failure.toUiMessage()
    is SaveTimeoutException -> IngredientUiMessage.SaveTimeout
    else -> IngredientUiMessage.UnexpectedError
}

private fun IngredientFailure.toUiMessage(): IngredientUiMessage = when (this) {
    IngredientFailure.NameRequired -> IngredientUiMessage.NameRequired
    IngredientFailure.NameTooLong -> IngredientUiMessage.NameTooLong
    IngredientFailure.CategoryTooLong -> IngredientUiMessage.CategoryTooLong
    IngredientFailure.UnitTooLong -> IngredientUiMessage.UnitTooLong
    IngredientFailure.AlreadyExists -> IngredientUiMessage.AlreadyExists
    IngredientFailure.NotAuthenticated -> IngredientUiMessage.NotAuthenticated
    IngredientFailure.EmailNotVerified -> IngredientUiMessage.EmailNotVerified
    IngredientFailure.IngredientNotFound -> IngredientUiMessage.NotFound
    IngredientFailure.PermissionDenied -> IngredientUiMessage.PermissionDenied
    IngredientFailure.Network -> IngredientUiMessage.NetworkError
    IngredientFailure.Unknown -> IngredientUiMessage.UnexpectedError
}

private fun OffFailure.toUiMessage(): IngredientUiMessage = when (this) {
    OffFailure.Network -> IngredientUiMessage.ScanFailed
    OffFailure.Unknown -> IngredientUiMessage.ScanFailed
}

private fun BrandFailure.toUiMessage(): IngredientUiMessage = when (this) {
    BrandFailure.NameRequired -> IngredientUiMessage.BrandNameRequired
    BrandFailure.NameTooLong -> IngredientUiMessage.BrandNameTooLong
    BrandFailure.BarcodeTooLong -> IngredientUiMessage.BarcodeTooLong
    BrandFailure.BrandNotFound -> IngredientUiMessage.BrandNotFound
    BrandFailure.NotAuthenticated -> IngredientUiMessage.NotAuthenticated
    BrandFailure.EmailNotVerified -> IngredientUiMessage.EmailNotVerified
    BrandFailure.PermissionDenied -> IngredientUiMessage.PermissionDenied
    BrandFailure.Network -> IngredientUiMessage.NetworkError
    BrandFailure.Unknown -> IngredientUiMessage.UnexpectedError
}

private fun IngredientPrefFailure.toUiMessage(): IngredientUiMessage = when (this) {
    IngredientPrefFailure.NotAuthenticated -> IngredientUiMessage.NotAuthenticated
    IngredientPrefFailure.EmailNotVerified -> IngredientUiMessage.EmailNotVerified
    IngredientPrefFailure.Network -> IngredientUiMessage.NetworkError
    IngredientPrefFailure.PermissionDenied -> IngredientUiMessage.PermissionDenied
    IngredientPrefFailure.Unknown -> IngredientUiMessage.UnexpectedError
}
