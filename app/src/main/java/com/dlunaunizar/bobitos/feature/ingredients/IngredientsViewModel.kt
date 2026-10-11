package com.dlunaunizar.bobitos.feature.ingredients

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
import com.dlunaunizar.bobitos.core.model.Nutrition
import com.dlunaunizar.bobitos.core.model.slug
import com.dlunaunizar.bobitos.data.openfoodfacts.OffException
import com.dlunaunizar.bobitos.data.openfoodfacts.OffFailure
import com.dlunaunizar.bobitos.data.openfoodfacts.OpenFoodFactsClient
import com.dlunaunizar.bobitos.data.repository.BrandFailure
import com.dlunaunizar.bobitos.data.repository.BrandInput
import com.dlunaunizar.bobitos.data.repository.BrandRepositoryException
import com.dlunaunizar.bobitos.data.repository.IngredientBrandRepository
import com.dlunaunizar.bobitos.data.repository.IngredientFailure
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepositoryException
import com.dlunaunizar.bobitos.data.repository.PersonalIngredientRepository
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
class IngredientsViewModel @Inject constructor(
    private val repository: IngredientRepository,
    private val personalRepository: PersonalIngredientRepository,
    private val prefsRepository: IngredientPrefsRepository,
    private val brandRepository: IngredientBrandRepository,
    private val openFoodFactsClient: OpenFoodFactsClient,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(IngredientsUiState())
    val uiState: StateFlow<IngredientsUiState> = mutableUiState.asStateFlow()

    private var catalogJob: Job? = null
    private var personalJob: Job? = null
    private var brandsJob: Job? = null
    private var prefsJob: Job? = null
    private var observing = false

    // Comunes (null mientras cargan) y personales; la lista de la pantalla es su mezcla.
    private var commonIngredients: List<CatalogIngredient>? = null
    private var personalIngredients: List<CatalogIngredient> = emptyList()

    fun observe() {
        if (observing) return
        observing = true
        mutableUiState.update { it.copy(isAdmin = repository.isCurrentUserCatalogAdmin()) }
        startCatalog()
        personalJob = viewModelScope.launch {
            personalRepository.ingredients()
                .catch { personalIngredients = emptyList() }
                .collect {
                    personalIngredients = it
                    publishIngredients()
                }
        }
        brandsJob = viewModelScope.launch {
            brandRepository.brands()
                .catch { mutableUiState.update { it.copy(brands = emptyList()) } }
                .collect { brands -> mutableUiState.update { it.copy(brands = brands) } }
        }
        prefsJob = viewModelScope.launch {
            prefsRepository.prefs()
                .catch { mutableUiState.update { it.copy(prefs = emptyMap()) } }
                .collect { prefs -> mutableUiState.update { it.copy(prefs = prefs) } }
        }
    }

    private fun startCatalog() {
        catalogJob = viewModelScope.launch {
            repository.catalog()
                .catch {
                    // Sin red y sin caché: mensaje propio, no el texto técnico de Firebase.
                    commonIngredients = null
                    mutableUiState.update {
                        it.copy(
                            ingredients = UiState.Error(),
                            catalogError = IngredientUiMessage.CatalogUnavailable,
                        )
                    }
                }
                .collect {
                    commonIngredients = it
                    publishIngredients()
                }
        }
    }

    private fun publishIngredients() {
        val commonList = commonIngredients ?: return
        val merged = mergeIngredients(commonList, personalIngredients)
        mutableUiState.update { it.copy(ingredients = UiState.Content(merged), catalogError = null) }
    }

    fun retryCatalog() {
        if (!observing) return
        catalogJob?.cancel()
        mutableUiState.update { it.copy(ingredients = UiState.Loading, catalogError = null) }
        startCatalog()
    }

    fun stopObserving() {
        listOf(catalogJob, personalJob, brandsJob, prefsJob).forEach { it?.cancel() }
        catalogJob = null
        personalJob = null
        brandsJob = null
        prefsJob = null
        observing = false
    }

    fun setQuery(query: String) {
        mutableUiState.update { it.copy(query = query) }
    }

    /** Alta de ingrediente: común solo si [common] y la cuenta es admin; si no, personal. */
    fun createIngredient(name: String, category: String?, defaultUnit: String?, common: Boolean) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            showError(IngredientUiMessage.NameRequired, editor = true)
            return
        }
        val existing = findByKey(currentIngredients(), trimmed)
        if (existing != null) {
            mutableUiState.update {
                it.copy(
                    isSaving = false,
                    error = IngredientUiMessage.AlreadyExists,
                    notice = null,
                    editorSave = it.editorSave.failed(true),
                    openIngredientId = existing.id,
                    existingName = existing.name,
                )
            }
            return
        }
        val asCommon = common && mutableUiState.value.isAdmin
        runAction(IngredientUiMessage.Saved, editor = true) {
            if (asCommon) {
                repository.createIngredient(trimmed, category, defaultUnit)
            } else {
                personalRepository.create(trimmed, category, defaultUnit)
            }
        }
    }

    fun consumeOpenIngredient() {
        mutableUiState.update { it.copy(openIngredientId = null) }
    }

    /** Consulta Open Food Facts por el código escaneado y deja un producto pendiente de convertir. */
    fun lookupBarcode(barcode: String) {
        if (mutableUiState.value.isLookingUp) return
        mutableUiState.update { it.copy(isLookingUp = true, error = null, notice = null, existingName = null) }
        viewModelScope.launch {
            try {
                val product = openFoodFactsClient.lookup(barcode)
                val suggested = product?.productName ?: product?.brand.orEmpty()
                mutableUiState.update {
                    it.copy(
                        isLookingUp = false,
                        scannedProduct = ScannedProduct(
                            suggestedName = suggested,
                            brandName = product?.brand ?: suggested,
                            barcode = barcode,
                            nutrition = product?.nutrition,
                        ),
                        notice = if (product == null) IngredientUiMessage.ScanNotFound else null,
                    )
                }
            } catch (error: OffException) {
                mutableUiState.update { it.copy(isLookingUp = false, error = error.failure.toUiMessage()) }
            }
        }
    }

    fun consumeScannedProduct() {
        mutableUiState.update { it.copy(scannedProduct = null) }
    }

    /** Crea el ingrediente (si no existe ya) y le añade la marca escaneada con su nutrición. */
    fun createIngredientFromScan(
        name: String,
        category: String?,
        defaultUnit: String?,
        brandName: String,
        barcode: String,
        nutrition: Nutrition?,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            showError(IngredientUiMessage.NameRequired, editor = true)
            return
        }
        val id = slug(trimmed)
        runAction(IngredientUiMessage.Saved, editor = true) {
            if (!catalogContains(id)) repository.createIngredient(trimmed, category, defaultUnit)
            brandRepository.addBrand(
                BrandInput(
                    ingredientId = id,
                    name = brandName.ifBlank { trimmed },
                    barcode = barcode.takeIf(String::isNotBlank),
                    nutrition = nutrition,
                    favorite = false,
                    stores = emptyList(),
                ),
            )
        }
    }

    fun consumeEditorSave() = mutableUiState.update { it.copy(editorSave = EditorSaveStatus.IDLE) }

    fun clearFeedback() {
        mutableUiState.update { it.copy(error = null, notice = null) }
    }

    private fun currentIngredients(): List<CatalogIngredient> =
        (mutableUiState.value.ingredients as? UiState.Content)?.value.orEmpty()

    private fun catalogContains(id: String): Boolean = currentIngredients().any { it.id == id }

    private fun showError(message: IngredientUiMessage, editor: Boolean = false) {
        mutableUiState.update {
            it.copy(isSaving = false, error = message, notice = null, editorSave = it.editorSave.failed(editor))
        }
    }

    private fun runAction(successNotice: IngredientUiMessage, editor: Boolean = false, action: suspend () -> Unit) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update {
            it.copy(
                isSaving = true,
                error = null,
                notice = null,
                existingName = null,
                editorSave = it.editorSave.started(editor),
            )
        }
        viewModelScope.launch {
            runCatching { withEditorSaveTimeout(editor) { action() } }
                .onSuccess {
                    mutableUiState.update {
                        it.copy(isSaving = false, notice = successNotice, editorSave = it.editorSave.succeeded(editor))
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    showError(error.toUiMessage(), editor)
                }
        }
    }
}

private fun Throwable.toUiMessage(): IngredientUiMessage = when (this) {
    is IngredientRepositoryException -> failure.toUiMessage()
    is BrandRepositoryException -> failure.toUiMessage()
    is SaveTimeoutException -> IngredientUiMessage.SaveTimeout
    else -> IngredientUiMessage.UnexpectedError
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
