package com.dlunaunizar.bobitos.feature.ingredients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.failed
import com.dlunaunizar.bobitos.core.common.started
import com.dlunaunizar.bobitos.core.common.succeeded
import com.dlunaunizar.bobitos.core.common.withSaveTimeout
import com.dlunaunizar.bobitos.core.model.Nutrition
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IngredientDetailViewModel @Inject constructor(
    private val repository: IngredientRepository,
    private val prefsRepository: IngredientPrefsRepository,
    private val brandRepository: IngredientBrandRepository,
    private val openFoodFactsClient: OpenFoodFactsClient,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(IngredientDetailUiState())
    val uiState: StateFlow<IngredientDetailUiState> = mutableUiState.asStateFlow()

    private var ingredientId: String = ""
    private var catalogJob: Job? = null
    private var brandsJob: Job? = null
    private var prefsJob: Job? = null

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
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            repository.catalog()
                .catch { /* el error del catálogo se refleja como ingrediente ausente */ }
                .collect { list ->
                    mutableUiState.update { it.copy(ingredient = list.firstOrNull { i -> i.id == id }, loaded = true) }
                }
        }
        brandsJob?.cancel()
        brandsJob = viewModelScope.launch {
            brandRepository.brands(id)
                .catch { mutableUiState.update { it.copy(brands = emptyList()) } }
                .collect { brands -> mutableUiState.update { it.copy(brands = brands) } }
        }
        prefsJob?.cancel()
        prefsJob = viewModelScope.launch {
            prefsRepository.prefs()
                .catch { mutableUiState.update { it.copy(pref = null) } }
                .collect { prefs -> mutableUiState.update { it.copy(pref = prefs[id]) } }
        }
    }

    fun stopObserving() {
        catalogJob?.cancel()
        brandsJob?.cancel()
        prefsJob?.cancel()
        catalogJob = null
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
            repository.updateIngredient(ingredientId, name.trim(), category, defaultUnit)
        }
    }

    fun deleteIngredient() {
        runAction(IngredientUiMessage.Deleted, finishOnSuccess = true) { repository.deleteIngredient(ingredientId) }
    }

    fun setPref(supermarket: Supermarket?, brand: String?) {
        runAction(IngredientUiMessage.PrefSaved) { prefsRepository.setPref(ingredientId, supermarket, brand) }
    }

    fun clearPref() {
        runAction(IngredientUiMessage.PrefCleared) { prefsRepository.clearPref(ingredientId) }
    }

    fun addBrand(name: String, barcode: String?, nutrition: Nutrition?) {
        if (name.trim().isEmpty()) {
            showError(IngredientUiMessage.BrandNameRequired, editor = true)
            return
        }
        runAction(IngredientUiMessage.BrandSaved, editor = true) {
            brandRepository.addBrand(ingredientId, name.trim(), barcode, nutrition)
        }
    }

    fun updateBrand(brandId: String, name: String, barcode: String?, nutrition: Nutrition?) {
        if (name.trim().isEmpty()) {
            showError(IngredientUiMessage.BrandNameRequired, editor = true)
            return
        }
        runAction(IngredientUiMessage.BrandSaved, editor = true) {
            brandRepository.updateBrand(ingredientId, brandId, name.trim(), barcode, nutrition)
        }
    }

    fun deleteBrand(brandId: String) {
        runAction(IngredientUiMessage.BrandDeleted) { brandRepository.deleteBrand(ingredientId, brandId) }
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
        notice: IngredientUiMessage,
        finishOnSuccess: Boolean = false,
        editor: Boolean = false,
        action: suspend () -> Unit,
    ) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update {
            it.copy(isSaving = true, error = null, notice = null, editorSave = it.editorSave.started(editor))
        }
        viewModelScope.launch {
            runCatching { withSaveTimeout { action() } }
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
    is IngredientPrefsException -> failure.toUiMessage()
    is BrandRepositoryException -> failure.toUiMessage()
    is SaveTimeoutException -> IngredientUiMessage.SaveTimeout
    else -> IngredientUiMessage.UnexpectedError
}

private fun IngredientFailure.toUiMessage(): IngredientUiMessage = when (this) {
    IngredientFailure.NameRequired -> IngredientUiMessage.NameRequired
    IngredientFailure.NameTooLong -> IngredientUiMessage.NameTooLong
    IngredientFailure.CategoryTooLong -> IngredientUiMessage.CategoryTooLong
    IngredientFailure.UnitTooLong -> IngredientUiMessage.UnitTooLong
    IngredientFailure.NotAuthenticated -> IngredientUiMessage.NotAuthenticated
    IngredientFailure.EmailNotVerified -> IngredientUiMessage.EmailNotVerified
    IngredientFailure.IngredientNotFound -> IngredientUiMessage.NotFound
    IngredientFailure.PermissionDenied -> IngredientUiMessage.PermissionDenied
    IngredientFailure.Network -> IngredientUiMessage.NetworkError
    IngredientFailure.Unknown -> IngredientUiMessage.UnexpectedError
}

private fun IngredientPrefFailure.toUiMessage(): IngredientUiMessage = when (this) {
    IngredientPrefFailure.NotAuthenticated -> IngredientUiMessage.NotAuthenticated
    IngredientPrefFailure.EmailNotVerified -> IngredientUiMessage.EmailNotVerified
    IngredientPrefFailure.PermissionDenied -> IngredientUiMessage.PermissionDenied
    IngredientPrefFailure.Network -> IngredientUiMessage.NetworkError
    IngredientPrefFailure.Unknown -> IngredientUiMessage.UnexpectedError
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
