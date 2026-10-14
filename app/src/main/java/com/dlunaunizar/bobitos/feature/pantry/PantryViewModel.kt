package com.dlunaunizar.bobitos.feature.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.core.model.PantryItem
import com.dlunaunizar.bobitos.core.model.pantryId
import com.dlunaunizar.bobitos.data.repository.PantryFailure
import com.dlunaunizar.bobitos.data.repository.PantryRepository
import com.dlunaunizar.bobitos.data.repository.PantryRepositoryException
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingData
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingSource
import com.dlunaunizar.bobitos.feature.common.firstOrEmpty
import com.dlunaunizar.bobitos.feature.common.pantryRestock
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
class PantryViewModel @Inject constructor(
    private val repository: PantryRepository,
    private val shoppingRepository: ShoppingRepository,
    private val ingredientSource: IngredientShoppingSource,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PantryUiState())
    val uiState: StateFlow<PantryUiState> = mutableUiState.asStateFlow()
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
                // Sin el texto de la excepción: la pantalla dice que no se ha podido leer y ofrece «Reintentar».
                .catch { mutableUiState.update { state -> state.copy(items = UiState.Error()) } }
                .collect { items -> mutableUiState.update { it.copy(items = UiState.Content(items)) } }
        }
    }

    fun stopObserving() {
        itemsJob?.cancel()
        itemsJob = null
        observedSpaceId = null
    }

    fun setQuery(query: String) = mutableUiState.update { it.copy(query = query) }

    /** «Se acabó»: quita [item]; cuando se confirma, la pantalla ofrece apuntarlo a la compra ([PantryUiState.finished]). */
    fun markFinished(item: PantryItem) {
        val spaceId = observedSpaceId ?: return
        runAction(onSuccess = { it.copy(finished = item) }) {
            repository.remove(spaceId, listOf(item.id))
            null
        }
    }

    fun consumeFinished() = mutableUiState.update { it.copy(finished = null) }

    /**
     * «A la compra»: apunta [item] en la Compra (ver [pantryRestock]); si ya estaba pendiente, solo lo avisa. No pasa
     * por [runAction]: el aviso del Snackbar ya se consumió y no debe perderse porque otra acción esté guardando.
     */
    fun restock(item: PantryItem) {
        val spaceId = observedSpaceId ?: return
        mutableUiState.update { it.copy(error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                // La lista del servidor, no la caché: con una vieja se duplicaría el artículo.
                val items = shoppingRepository.currentItems(spaceId)
                val plan = pantryRestock(listOf(item.name), items, ingredientData())
                if (plan.writes.isEmpty()) {
                    PantryUiMessage.AlreadyInShopping
                } else {
                    shoppingRepository.applyWrites(spaceId, plan.writes)
                    PantryUiMessage.AddedToShopping
                }
            }
                .onSuccess { notice -> mutableUiState.update { it.copy(notice = notice) } }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    mutableUiState.update { it.copy(error = error.toUiMessage()) }
                }
        }
    }

    fun openPicker() {
        if (mutableUiState.value.picker != null) return
        mutableUiState.update { it.copy(picker = PantryPickerState()) }
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            ingredientSource.data().collect { data ->
                mutableUiState.update { state ->
                    state.copy(ingredientData = data, picker = state.picker?.copy(catalogLoaded = true))
                }
            }
        }
    }

    fun closePicker() {
        stopCatalog()
        mutableUiState.update { it.copy(picker = null) }
    }

    fun setPickerQuery(query: String) = mutableUiState.update { it.copy(picker = it.picker?.copy(query = query)) }

    fun togglePick(entry: PantryEntry) = mutableUiState.update { state ->
        val picker = state.picker ?: return@update state
        val id = pantryId(entry.name)
        val selection = if (id in picker.selection) picker.selection - id else picker.selection + (id to entry)
        state.copy(picker = picker.copy(selection = selection))
    }

    /** Apunta lo marcado; si sale bien, cierra el selector. Si falla, lo deja abierto con lo marcado. */
    fun confirmPicker() {
        val spaceId = observedSpaceId ?: return
        val entries = mutableUiState.value.picker?.selection?.values?.toList().orEmpty()
        if (entries.isEmpty()) return
        runAction(onSuccess = { it.copy(picker = null) }) {
            repository.add(spaceId, entries)
            stopCatalog()
            PantryUiMessage.ItemsAdded
        }
    }

    private fun stopCatalog() {
        catalogJob?.cancel()
        catalogJob = null
    }

    fun clearFeedback() = mutableUiState.update { it.copy(error = null, notice = null) }

    // Marcas y tiendas para un artículo nuevo: las que ya cargó el selector; si no, se esperan (5 s como máximo) y,
    // si no llegan a tiempo, se apunta sin marca ni tienda.
    private suspend fun ingredientData(): IngredientShoppingData =
        mutableUiState.value.ingredientData.takeIf { it.ingredients.isNotEmpty() } ?: ingredientSource.firstOrEmpty()

    // Ejecuta [action] (devuelve el aviso de éxito) y, si sale bien, aplica [onSuccess] al estado.
    private fun runAction(
        onSuccess: (PantryUiState) -> PantryUiState = { it },
        action: suspend () -> PantryUiMessage?,
    ) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update { it.copy(isSaving = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { notice -> mutableUiState.update { onSuccess(it.copy(isSaving = false, notice = notice)) } }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    mutableUiState.update { it.copy(isSaving = false, error = error.toUiMessage()) }
                }
        }
    }
}

private fun Throwable.toUiMessage(): PantryUiMessage = when (this) {
    is PantryRepositoryException -> failure.toUiMessage()
    is ShoppingRepositoryException -> failure.toUiMessage()
    else -> PantryUiMessage.UnexpectedError
}

private fun PantryFailure.toUiMessage(): PantryUiMessage = when (this) {
    PantryFailure.NameRequired -> PantryUiMessage.NameRequired
    PantryFailure.NameTooLong -> PantryUiMessage.NameTooLong
    PantryFailure.NotAuthenticated -> PantryUiMessage.NotAuthenticated
    PantryFailure.EmailNotVerified -> PantryUiMessage.EmailNotVerified
    PantryFailure.SpaceNotFound -> PantryUiMessage.SpaceNotFound
    PantryFailure.PermissionDenied -> PantryUiMessage.PermissionDenied
    PantryFailure.Network -> PantryUiMessage.NetworkError
    PantryFailure.Unknown -> PantryUiMessage.UnexpectedError
}

private fun ShoppingFailure.toUiMessage(): PantryUiMessage = when (this) {
    ShoppingFailure.ItemChanged, ShoppingFailure.ItemNotFound -> PantryUiMessage.ShoppingChanged
    ShoppingFailure.NotAuthenticated -> PantryUiMessage.NotAuthenticated
    ShoppingFailure.EmailNotVerified -> PantryUiMessage.EmailNotVerified
    ShoppingFailure.SpaceNotFound -> PantryUiMessage.SpaceNotFound
    ShoppingFailure.PermissionDenied -> PantryUiMessage.PermissionDenied
    ShoppingFailure.Network -> PantryUiMessage.NetworkError
    else -> PantryUiMessage.UnexpectedError
}
