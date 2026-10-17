package com.dlunaunizar.bobitos.feature.ingredients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.failed
import com.dlunaunizar.bobitos.core.common.started
import com.dlunaunizar.bobitos.core.common.succeeded
import com.dlunaunizar.bobitos.core.common.withEditorSaveTimeout
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.findByKey
import com.dlunaunizar.bobitos.data.openfoodfacts.OffException
import com.dlunaunizar.bobitos.data.openfoodfacts.OpenFoodFactsClient
import com.dlunaunizar.bobitos.data.repository.DeletedIngredient
import com.dlunaunizar.bobitos.data.repository.IngredientBrandRepository
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
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
    private val deletedRelay: DeletedIngredientRelay,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(IngredientsUiState())
    val uiState: StateFlow<IngredientsUiState> = mutableUiState.asStateFlow()

    // Ingrediente personal recién borrado desde su detalle: la pantalla ofrece «Deshacer» y lo consume.
    val deleted: StateFlow<DeletedIngredient?> = deletedRelay.deleted

    fun consumeDeleted() = deletedRelay.consume()

    // «Deshacer» el borrado: devuelve el ingrediente con sus marcas y tiendas (si no puede, sale el error).
    fun restoreIngredient(deleted: DeletedIngredient) =
        runAction(null, force = true) { personalRepository.restore(deleted) }

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
        // El ingrediente personal deja `created` (con «Deshacer») en lugar del aviso de guardado; el común, el de siempre.
        runAction(if (asCommon) IngredientUiMessage.Saved else null, editor = true) {
            if (asCommon) {
                repository.createIngredient(trimmed, category, defaultUnit)
            } else {
                CreatedItem(personalRepository.create(trimmed, category, defaultUnit), trimmed)
            }
        }
    }

    fun consumeCreated() = mutableUiState.update { it.copy(created = null) }

    // «Deshacer» el alta del ingrediente personal: lo borra con el borrado de siempre (sin aviso; si falla, sale el error).
    fun undoCreate(id: String) = runAction(null, force = true) { personalRepository.delete(id) }

    fun consumeOpenIngredient() {
        mutableUiState.update { it.copy(openIngredientId = null) }
    }

    /** Un código ya guardado abre su marca; si no, consulta Open Food Facts y deja la elección de ingrediente. */
    fun lookupBarcode(barcode: String) {
        if (mutableUiState.value.isLookingUp) return
        // Una marca huérfana (su ingrediente común ya no existe) se trata como código nuevo.
        val visibleIds = currentIngredients().mapTo(HashSet(), CatalogIngredient::id)
        val known = mutableUiState.value.brands.filter { it.ingredientId in visibleIds }
        findBrandByBarcode(known, barcode)?.let { brand ->
            mutableUiState.update { it.copy(openBrand = OpenBrand(brand.ingredientId, brand.id)) }
            return
        }
        mutableUiState.update { it.copy(isLookingUp = true, error = null, notice = null, existingName = null) }
        viewModelScope.launch {
            try {
                val product = openFoodFactsClient.lookup(barcode)
                val productName = product?.productName ?: product?.brand.orEmpty()
                mutableUiState.update {
                    it.copy(
                        isLookingUp = false,
                        scanPick = ScanPick(barcode, productName, product?.brand ?: productName, product?.nutrition),
                        notice = if (product == null) IngredientUiMessage.ScanNotFound else null,
                    )
                }
            } catch (_: OffException) {
                // Sin red se sigue a mano: solo se conoce el código.
                mutableUiState.update {
                    it.copy(
                        isLookingUp = false,
                        scanPick = ScanPick(barcode, "", "", null),
                        error = IngredientUiMessage.ScanFailed,
                    )
                }
            }
        }
    }

    fun consumeOpenBrand() {
        mutableUiState.update { it.copy(openBrand = null) }
    }

    fun pickIngredientForScan(ingredientId: String) {
        mutableUiState.update { it.copy(scanBrandTarget = ingredientId) }
    }

    /** Crea el ingrediente personal del escaneo; si su clave ya existe, usa el existente. */
    fun createPersonalForScan(name: String, category: String?, defaultUnit: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            showError(IngredientUiMessage.NameRequired, editor = true)
            return
        }
        val existing = findByKey(currentIngredients(), trimmed)
        if (existing != null) {
            pickIngredientForScan(existing.id)
            return
        }
        runAction(IngredientUiMessage.Saved, editor = true) {
            val id = personalRepository.create(trimmed, category, defaultUnit)
            mutableUiState.update { it.copy(scanBrandTarget = id) }
        }
    }

    /** Guarda la marca escaneada en el ingrediente elegido y termina el flujo. */
    internal fun saveScannedBrand(draft: BrandDraft) {
        val target = mutableUiState.value.scanBrandTarget ?: return
        if (draft.name.trim().isEmpty()) {
            showError(IngredientUiMessage.BrandNameRequired, editor = true)
            return
        }
        runAction(IngredientUiMessage.BrandSaved, editor = true) {
            brandRepository.addBrand(draft.toInput(target))
            mutableUiState.update { it.copy(scanPick = null, scanBrandTarget = null) }
        }
    }

    fun cancelScan() {
        // Un error del diálogo de marca que se cierra ya se vio en él: no reaparece como aviso.
        mutableUiState.update {
            it.copy(
                scanPick = null,
                scanBrandTarget = null,
                error = if (it.editorSave == EditorSaveStatus.FAILED) null else it.error,
            )
        }
    }

    fun consumeEditorSave() = mutableUiState.update { it.copy(editorSave = EditorSaveStatus.IDLE) }

    fun clearFeedback() {
        mutableUiState.update { it.copy(error = null, notice = null) }
    }

    private fun currentIngredients(): List<CatalogIngredient> =
        (mutableUiState.value.ingredients as? UiState.Content)?.value.orEmpty()

    private fun showError(message: IngredientUiMessage, editor: Boolean = false) {
        mutableUiState.update {
            it.copy(isSaving = false, error = message, notice = null, editorSave = it.editorSave.failed(editor))
        }
    }

    // Si la acción devuelve un [CreatedItem], es un alta desde la hoja y queda en `created`.
    private fun runAction(
        successNotice: IngredientUiMessage?,
        editor: Boolean = false,
        force: Boolean = false,
        action: suspend () -> Any?,
    ) {
        if (mutableUiState.value.isSaving && !force) return
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
                .onSuccess { result ->
                    mutableUiState.update {
                        it.copy(
                            isSaving = false,
                            notice = successNotice,
                            created = result as? CreatedItem,
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
