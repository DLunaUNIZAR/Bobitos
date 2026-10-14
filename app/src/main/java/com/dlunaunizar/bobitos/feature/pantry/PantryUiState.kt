package com.dlunaunizar.bobitos.feature.pantry

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.core.model.PantryItem
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingData

data class PantryUiState(
    val items: UiState<List<PantryItem>> = UiState.Loading,
    val query: String = "",
    val isSaving: Boolean = false,
    /** Lo último que se ha acabado: la pantalla ofrece apuntarlo a la compra con un Snackbar. */
    val finished: PantryItem? = null,
    val error: PantryUiMessage? = null,
    val notice: PantryUiMessage? = null,
    /** Selector «Añadir a la despensa»; `null` si está cerrado. */
    val picker: PantryPickerState? = null,
    /** Comunes y personales (con marcas y tiendas), que se escuchan mientras el selector está abierto. */
    val ingredientData: IngredientShoppingData = IngredientShoppingData(),
)

data class PantryPickerState(
    val query: String = "",
    /** Lo marcado, por `pantryId`. */
    val selection: Map<String, PantryEntry> = emptyMap(),
    val catalogLoaded: Boolean = false,
)

// Mensajes de la Despensa: fallos del repositorio (y de la Compra, en «A la compra») y avisos de éxito.
// La pantalla los mapea a recursos de string.
enum class PantryUiMessage {
    NameRequired,
    NameTooLong,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    PermissionDenied,
    NetworkError,
    ShoppingChanged,
    UnexpectedError,
    ItemsAdded,
    AddedToShopping,
    AlreadyInShopping,
}
