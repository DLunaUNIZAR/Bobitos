package com.dlunaunizar.bobitos.feature.ingredients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.SaveFeedbackControls
import com.dlunaunizar.bobitos.core.common.SaveRunner
import com.dlunaunizar.bobitos.core.common.UiState
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

    private val saver = SaveRunner(
        viewModelScope,
        mutableUiState,
        IngredientsUiState::save,
        { copy(save = it) },
        Throwable::toUiMessage,
    )
    val feedback: SaveFeedbackControls get() = saver

    // Ingrediente personal recién borrado desde su detalle: la pantalla ofrece «Deshacer» y lo consume.
    // Solo se ofrece si lo borró la cuenta actual: el relevo vive lo que el proceso y la sesión puede haber cambiado.
    private val mutableDeleted = MutableStateFlow<DeletedIngredient?>(null)
    val deleted: StateFlow<DeletedIngredient?> = mutableDeleted.asStateFlow()

    fun consumeDeleted() = deletedRelay.consume()

    // «Deshacer» el borrado: devuelve el ingrediente con sus marcas y tiendas (si no puede, sale el error).
    fun restoreIngredient(deleted: DeletedIngredient) {
        if (!deleted.isOwnedByCurrentUser()) return
        saver.run(force = true) { personalRepository.restore(deleted) }
    }

    private fun DeletedIngredient.isOwnedByCurrentUser(): Boolean =
        ownerUid != null && ownerUid == repository.currentUserId()

    private var catalogJob: Job? = null
    private var personalJob: Job? = null
    private var brandsJob: Job? = null
    private var prefsJob: Job? = null
    private var relayJob: Job? = null
    private var observing = false

    // Comunes (null mientras cargan) y personales; la lista de la pantalla es su mezcla.
    private var commonIngredients: List<CatalogIngredient>? = null
    private var personalIngredients: List<CatalogIngredient> = emptyList()

    fun observe() {
        if (observing) return
        observing = true
        mutableUiState.update { it.copy(isAdmin = repository.isCurrentUserCatalogAdmin()) }
        startCatalog()
        relayJob = viewModelScope.launch {
            deletedRelay.deleted.collect { snapshot ->
                if (snapshot == null || snapshot.isOwnedByCurrentUser()) {
                    mutableDeleted.value = snapshot
                } else {
                    deletedRelay.consume()
                }
            }
        }
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
        listOf(catalogJob, personalJob, brandsJob, prefsJob, relayJob).forEach { it?.cancel() }
        catalogJob = null
        personalJob = null
        brandsJob = null
        prefsJob = null
        relayJob = null
        observing = false
    }

    /** Alta de ingrediente: común solo si [common] y la cuenta es admin; si no, personal. */
    fun createIngredient(name: String, category: String?, defaultUnit: String?, common: Boolean) {
        mutableUiState.update { it.copy(existingName = null) }
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            saver.showEditorError(IngredientUiMessage.NameRequired)
            return
        }
        val existing = findByKey(currentIngredients(), trimmed)
        if (existing != null) {
            // El aviso se ve una sola vez, en el formulario: la pantalla abre la ficha existente.
            saver.showEditorError(IngredientUiMessage.AlreadyExists)
            mutableUiState.update { it.copy(openIngredientId = existing.id, existingName = existing.name) }
            return
        }
        val asCommon = common && mutableUiState.value.isAdmin
        // El ingrediente personal deja `created` (con «Deshacer») en lugar del aviso de guardado; el común, el de siempre.
        if (asCommon) {
            saver.run(notice = IngredientUiMessage.Saved, editor = true) {
                repository.createIngredient(trimmed, category, defaultUnit)
            }
        } else {
            saver.create(editor = true) {
                CreatedItem(personalRepository.create(trimmed, category, defaultUnit), trimmed)
            }
        }
    }

    // «Deshacer» el alta del ingrediente personal: lo borra con el borrado de siempre (sin aviso; si falla, sale el error).
    fun undoCreate(id: String) = saver.run(force = true) { personalRepository.delete(id) }

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
        saver.clearFeedback()
        mutableUiState.update { it.copy(isLookingUp = true, existingName = null) }
        viewModelScope.launch {
            try {
                val product = openFoodFactsClient.lookup(barcode)
                val productName = product?.productName ?: product?.brand.orEmpty()
                mutableUiState.update {
                    it.copy(
                        isLookingUp = false,
                        scanPick = ScanPick(barcode, productName, product?.brand ?: productName, product?.nutrition),
                    )
                }
                if (product == null) saver.showNotice(IngredientUiMessage.ScanNotFound)
            } catch (_: OffException) {
                // Sin red se sigue a mano: solo se conoce el código.
                mutableUiState.update { it.copy(isLookingUp = false, scanPick = ScanPick(barcode, "", "", null)) }
                saver.showError(IngredientUiMessage.ScanFailed)
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
            saver.showEditorError(IngredientUiMessage.NameRequired)
            return
        }
        val existing = findByKey(currentIngredients(), trimmed)
        if (existing != null) {
            pickIngredientForScan(existing.id)
            return
        }
        saver.run(
            notice = IngredientUiMessage.Saved,
            editor = true,
            onSuccess = { id -> copy(scanBrandTarget = id) },
        ) {
            personalRepository.create(trimmed, category, defaultUnit)
        }
    }

    /** Guarda la marca escaneada en el ingrediente elegido y termina el flujo. */
    internal fun saveScannedBrand(draft: BrandDraft) {
        val target = mutableUiState.value.scanBrandTarget ?: return
        if (draft.name.trim().isEmpty()) {
            saver.showEditorError(IngredientUiMessage.BrandNameRequired)
            return
        }
        saver.run(
            notice = IngredientUiMessage.BrandSaved,
            editor = true,
            onSuccess = { copy(scanPick = null, scanBrandTarget = null) },
        ) {
            brandRepository.addBrand(draft.toInput(target))
        }
    }

    fun cancelScan() {
        // Un error del diálogo de marca que se cierra ya se vio en él: no reaparece como aviso.
        mutableUiState.update { it.copy(scanPick = null, scanBrandTarget = null) }
        saver.resetEditor()
    }

    private fun currentIngredients(): List<CatalogIngredient> =
        (mutableUiState.value.ingredients as? UiState.Content)?.value.orEmpty()
}
