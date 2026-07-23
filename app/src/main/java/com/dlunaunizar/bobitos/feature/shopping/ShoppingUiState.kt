package com.dlunaunizar.bobitos.feature.shopping

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.ShoppingItem

data class ShoppingUiState(
    val items: UiState<List<ShoppingItem>> = UiState.Loading,
    val isSaving: Boolean = false,
    val writeStatus: ShoppingWriteStatus = ShoppingWriteStatus.IDLE,
    val error: ShoppingUiMessage? = null,
    val notice: ShoppingUiMessage? = null,
    val lastClearedCount: Int = 0,
    // Catálogo de ingredientes y preferencias del usuario, cargados de forma perezosa mientras el
    // editor de ítem está abierto: autocompletan el nombre y prerrellenan supermercado/marca.
    val catalog: List<CatalogIngredient> = emptyList(),
    val ingredientPrefs: Map<String, IngredientPref> = emptyMap(),
)

enum class ShoppingWriteStatus {
    IDLE,
    SAVING,
    SAVED,
    ERROR,
}
enum class ShoppingUiMessage {
    NameRequired,
    NameTooLong,
    QuantityTooLong,
    NotesTooLong,
    BrandTooLong,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    ItemNotFound,
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    ItemAdded,
    ItemUpdated,
    ItemMarked,
    ItemUnmarked,
    ItemDeleted,
    PurchasedCleared,
    PrefSaved,
    IngredientCreated,
    IngredientExists,
}
