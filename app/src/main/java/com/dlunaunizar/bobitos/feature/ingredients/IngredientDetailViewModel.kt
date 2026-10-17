package com.dlunaunizar.bobitos.feature.ingredients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.failed
import com.dlunaunizar.bobitos.core.common.started
import com.dlunaunizar.bobitos.core.common.succeeded
import com.dlunaunizar.bobitos.core.common.withEditorSaveTimeout
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.openfoodfacts.OffException
import com.dlunaunizar.bobitos.data.openfoodfacts.OpenFoodFactsClient
import com.dlunaunizar.bobitos.data.repository.DeletedIngredient
import com.dlunaunizar.bobitos.data.repository.IngredientBrandRepository
import com.dlunaunizar.bobitos.data.repository.IngredientPrefFailure
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsException
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
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
    private val deletedRelay: DeletedIngredientRelay,
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

    // La ficha está cargada cuando las dos fuentes han emitido o han fallado.
    private var catalogDone = false
    private var personalDone = false

    // Las marcas de todos los ingredientes, para reconocer un código escaneado que ya está en otro.
    private var allBrands: List<IngredientBrand> = emptyList()

    fun observe(id: String) {
        if (id == ingredientId && catalogJob?.isActive == true) return
        ingredientId = id
        mutableUiState.update {
            it.copy(
                ingredientId = id,
                isAdmin = repository.isCurrentUserCatalogAdmin(),
            )
        }
        common = emptyList()
        personal = emptyList()
        catalogDone = false
        personalDone = false
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            repository.catalog()
                .catch {
                    // Un fallo del catálogo cuenta como catálogo vacío: se resuelve con los personales.
                    common = emptyList()
                    catalogDone = true
                    publishIngredient(id)
                }
                .collect { list ->
                    common = list
                    catalogDone = true
                    publishIngredient(id)
                }
        }
        personalJob?.cancel()
        personalJob = viewModelScope.launch {
            personalRepository.ingredients()
                .catch {
                    personal = emptyList()
                    personalDone = true
                    publishIngredient(id)
                }
                .collect { list ->
                    personal = list
                    personalDone = true
                    publishIngredient(id)
                }
        }
        brandsJob?.cancel()
        brandsJob = viewModelScope.launch {
            brandRepository.brands()
                .catch {
                    allBrands = emptyList()
                    mutableUiState.update { it.copy(brands = emptyList()) }
                }
                .collect { all ->
                    allBrands = all
                    val brands = all.filter { it.ingredientId == id }.sortedForBrands()
                    mutableUiState.update { it.copy(brands = brands) }
                }
        }
        prefsJob?.cancel()
        prefsJob = viewModelScope.launch {
            prefsRepository.prefs()
                .map { it[id]?.stores.orEmpty() }
                .catch { mutableUiState.update { it.copy(stores = emptyList()) } }
                .collect { stores -> mutableUiState.update { it.copy(stores = stores) } }
        }
    }

    private fun publishIngredient(id: String) {
        val found = findIngredient(id)
        val loaded = catalogDone && personalDone
        mutableUiState.update { it.copy(ingredient = found, loaded = it.loaded || loaded) }
    }

    // El común gana al personal con el mismo id; sin ordenar todo el catálogo.
    private fun findIngredient(id: String) = common.firstOrNull { it.id == id } ?: personal.firstOrNull { it.id == id }

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

    // Un personal se borra sin aviso propio: tras terminar bien, la lista (adonde vuelve la pantalla) ofrece el
    // «Deshacer» con lo que se llevó por delante. El común (solo el admin) conserva su aviso y no se deshace.
    fun deleteIngredient() {
        val current = mutableUiState.value
        val snapshot = current.ingredient?.takeIf(CatalogIngredient::personal)
            ?.let { DeletedIngredient(it, current.brands, current.stores, repository.currentUserId()) }
        runAction(
            if (snapshot == null) IngredientUiMessage.Deleted else null,
            finishOnSuccess = true,
            onSuccess = { snapshot?.let(deletedRelay::post) },
        ) {
            if (snapshot != null) personalRepository.delete(ingredientId) else repository.deleteIngredient(ingredientId)
        }
    }

    private fun isPersonal() = mutableUiState.value.ingredient?.personal == true

    /**
     * Añade o quita [store] de «Dónde lo compro»; la lista guardada conserva el orden de elección.
     * Escritura optimista: la lista cambia al instante y no pasa por `isSaving` (cada toque es independiente);
     * si falla, se avisa y el siguiente snapshot del listener restaura el estado real.
     */
    fun toggleStore(store: Supermarket) {
        val current = mutableUiState.value.stores
        val stores = toggleStore(current, store)
        if (stores == current) return
        mutableUiState.update { it.copy(stores = stores) }
        val id = ingredientId
        writeOptimistically { prefsRepository.setStores(id, stores) }
    }

    /** Igual que [toggleStore]: la estrella cambia al instante y la escritura no usa `isSaving`. */
    fun setFavorite(brandId: String, favorite: Boolean) {
        mutableUiState.update { state ->
            state.copy(
                brands = state.brands
                    .map { if (it.id == brandId) it.copy(favorite = favorite) else it }
                    .sortedForBrands(),
            )
        }
        writeOptimistically { brandRepository.setFavorite(brandId, favorite) }
    }

    private fun writeOptimistically(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }.onFailure { error ->
                if (error is CancellationException) throw error
                mutableUiState.update { it.copy(error = error.toDetailUiMessage(), notice = null) }
            }
        }
    }

    /** Alta ([brandId] nulo) o edición de una marca desde el editor. */
    internal fun saveBrand(brandId: String?, draft: BrandDraft) {
        if (draft.name.trim().isEmpty()) {
            showError(IngredientUiMessage.BrandNameRequired, editor = true)
            return
        }
        val input = draft.toInput(ingredientId)
        val name = draft.name.trim()
        // Una marca nueva deja `created` (con «Deshacer») en lugar del aviso de marca guardada; editarla, el de siempre.
        runAction(if (brandId == null) null else IngredientUiMessage.BrandSaved, editor = true) {
            if (brandId == null) {
                CreatedItem(brandRepository.addBrand(input), name)
            } else {
                brandRepository.updateBrand(brandId, input)
            }
        }
    }

    fun consumeCreated() = mutableUiState.update { it.copy(created = null) }

    // «Deshacer» el alta de la marca: la borra con el borrado de siempre (sin aviso; si falla, sale el error).
    fun undoCreate(id: String) = runAction(null, force = true) { brandRepository.deleteBrand(id) }

    // onDeleted se llama solo si el borrado termina bien: así el «Deshacer» no se ofrece para un borrado fallido.
    fun deleteBrand(brandId: String, onDeleted: () -> Unit = {}) {
        runAction(null, onSuccess = onDeleted) { brandRepository.deleteBrand(brandId) }
    }

    // «Deshacer» el borrado de una marca (con el mismo id).
    fun restoreBrand(brand: IngredientBrand) {
        runAction(null, force = true) { brandRepository.restoreBrand(brand) }
    }

    /**
     * Un código que ya es de una marca abre esa marca (o avisa si es de otro ingrediente); si no, consulta
     * Open Food Facts y deja un borrador de marca prerrellenado.
     */
    fun lookupBarcode(barcode: String) {
        if (mutableUiState.value.isLookingUp) return
        findBrandByBarcode(allBrands, barcode)?.let { brand ->
            showKnownBrand(brand)
            return
        }
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
            } catch (_: OffException) {
                mutableUiState.update {
                    it.copy(isLookingUp = false, error = IngredientUiMessage.ScanFailed)
                }
            }
        }
    }

    private fun showKnownBrand(brand: IngredientBrand) {
        if (brand.ingredientId == ingredientId) {
            mutableUiState.update { it.copy(openBrandId = brand.id) }
            return
        }
        val owner = findIngredient(brand.ingredientId)
        mutableUiState.update {
            it.copy(
                error = null,
                notice = IngredientUiMessage.BarcodeInOtherIngredient,
                barcodeConflict = BarcodeConflict(brand.name, owner?.name ?: brand.ingredientId),
            )
        }
    }

    fun consumeOpenBrand() {
        mutableUiState.update { it.copy(openBrandId = null) }
    }

    fun consumeScannedBrand() {
        mutableUiState.update { it.copy(scannedBrand = null) }
    }

    fun consumeEditorSave() = mutableUiState.update { it.copy(editorSave = EditorSaveStatus.IDLE) }

    fun clearFeedback() {
        mutableUiState.update { it.copy(error = null, notice = null, barcodeConflict = null) }
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
        force: Boolean = false,
        onSuccess: () -> Unit = {},
        // Si la acción devuelve un [CreatedItem], es un alta desde la hoja y queda en `created`.
        action: suspend () -> Any?,
    ) {
        if (mutableUiState.value.isSaving && !force) return
        mutableUiState.update {
            it.copy(isSaving = true, error = null, notice = null, editorSave = it.editorSave.started(editor))
        }
        viewModelScope.launch {
            runCatching { withEditorSaveTimeout(editor) { action() } }
                .onSuccess { result ->
                    mutableUiState.update {
                        it.copy(
                            isSaving = false,
                            notice = notice,
                            created = result as? CreatedItem,
                            finished = it.finished || finishOnSuccess,
                            editorSave = it.editorSave.succeeded(editor),
                        )
                    }
                    onSuccess()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    showError(error.toDetailUiMessage(), editor)
                }
        }
    }
}

// Utilidad para el formulario de nutrición: acepta coma o punto decimal; vacío → null.
internal fun parseNutritionValue(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()

// Los fallos de preferencias solo se dan en el detalle (tiendas de «Dónde lo compro»).
private fun Throwable.toDetailUiMessage(): IngredientUiMessage =
    if (this is IngredientPrefsException) failure.toUiMessage() else toUiMessage()

private fun IngredientPrefFailure.toUiMessage(): IngredientUiMessage = when (this) {
    IngredientPrefFailure.NotAuthenticated -> IngredientUiMessage.NotAuthenticated
    IngredientPrefFailure.EmailNotVerified -> IngredientUiMessage.EmailNotVerified
    IngredientPrefFailure.Network -> IngredientUiMessage.NetworkError
    IngredientPrefFailure.PermissionDenied -> IngredientUiMessage.PermissionDenied
    IngredientPrefFailure.Unknown -> IngredientUiMessage.UnexpectedError
}
