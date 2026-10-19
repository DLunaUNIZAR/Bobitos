package com.dlunaunizar.bobitos.feature.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.SaveFeedbackControls
import com.dlunaunizar.bobitos.core.common.SaveRunner
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.rethrowIfCancelled
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
import com.dlunaunizar.bobitos.feature.common.restockFromPantry
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
class PantryViewModel @Inject constructor(
    private val repository: PantryRepository,
    private val shoppingRepository: ShoppingRepository,
    private val ingredientSource: IngredientShoppingSource,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PantryUiState())
    val uiState: StateFlow<PantryUiState> = mutableUiState.asStateFlow()

    // Sin editor ni tiempo máximo: la Despensa solo tiene el selector, que es un diálogo y no un editor.
    private val saver =
        SaveRunner(viewModelScope, mutableUiState, PantryUiState::save, { copy(save = it) }, Throwable::toUiMessage)
    val feedback: SaveFeedbackControls get() = saver
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
        saver.run(onSuccess = { copy(finished = item) }) {
            repository.remove(spaceId, listOf(item.id))
        }
    }

    fun consumeFinished() = mutableUiState.update { it.copy(finished = null) }

    /**
     * «A la compra»: apunta [item] en la Compra (ver [restockFromPantry]); si ya estaba pendiente, solo lo avisa. No
     * enciende «Guardando…» ni bloquea otras acciones: su fallo y su aviso van directos a la pantalla.
     */
    fun restock(item: PantryItem) {
        val spaceId = observedSpaceId ?: return
        viewModelScope.launch {
            runCatching {
                val plan = restockFromPantry(spaceId, listOf(item.name), shoppingRepository, ingredientData())
                if (plan.writes.isEmpty()) PantryUiMessage.AlreadyInShopping else PantryUiMessage.AddedToShopping
            }
                .onSuccess(saver::showNotice)
                .onFailure { error ->
                    error.rethrowIfCancelled()
                    saver.showError(error.toUiMessage())
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
        // El error que se veía dentro del selector se da por visto: no vuelve a salir en el aviso de la pantalla.
        saver.consumeError()
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
        // El error que el selector mostraba (el del «Añadir» anterior) se suelta al reintentar; el aviso no se toca.
        saver.consumeError()
        saver.run(noticeOf = { PantryUiMessage.ItemsAdded }, onSuccess = { copy(picker = null) }) {
            repository.add(spaceId, entries)
            stopCatalog()
        }
    }

    private fun stopCatalog() {
        catalogJob?.cancel()
        catalogJob = null
    }

    // Marcas y tiendas para un artículo nuevo: las que ya cargó el selector; si no, se esperan (5 s como máximo) y,
    // si no llegan a tiempo, se apunta sin marca ni tienda.
    private suspend fun ingredientData(): IngredientShoppingData =
        mutableUiState.value.ingredientData.takeIf { it.ingredients.isNotEmpty() } ?: ingredientSource.firstOrEmpty()
}

// Una sola traducción para los fallos de la Despensa y de la Compra: los comunes se leen por su nombre.
private fun Throwable.toUiMessage(): PantryUiMessage = when (this) {
    is PantryRepositoryException -> when (failure) {
        PantryFailure.NameRequired -> PantryUiMessage.NameRequired
        PantryFailure.NameTooLong -> PantryUiMessage.NameTooLong
        else -> failure.name.toCommonMessage()
    }
    is ShoppingRepositoryException -> when (failure) {
        ShoppingFailure.ItemChanged, ShoppingFailure.ItemNotFound -> PantryUiMessage.ShoppingChanged
        else -> failure.name.toCommonMessage()
    }
    else -> PantryUiMessage.UnexpectedError
}

// Los fallos con el mismo nombre en `PantryFailure` y `ShoppingFailure`; el resto es un error inesperado.
private fun String.toCommonMessage(): PantryUiMessage = when (this) {
    "NotAuthenticated" -> PantryUiMessage.NotAuthenticated
    "EmailNotVerified" -> PantryUiMessage.EmailNotVerified
    "SpaceNotFound" -> PantryUiMessage.SpaceNotFound
    "PermissionDenied" -> PantryUiMessage.PermissionDenied
    "Network" -> PantryUiMessage.NetworkError
    else -> PantryUiMessage.UnexpectedError
}
