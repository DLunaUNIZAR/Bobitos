package com.dlunaunizar.bobitos.feature.shopping

import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.ShoppingItem

data class ShoppingUiState(
    val items: UiState<List<ShoppingItem>> = UiState.Loading,
    val isSaving: Boolean = false,
    val writeStatus: ShoppingWriteStatus = ShoppingWriteStatus.IDLE,
    val editorSave: EditorSaveStatus = EditorSaveStatus.IDLE,
    val error: ShoppingUiMessage? = null,
    val notice: ShoppingUiMessage? = null,
    val lastClearedCount: Int = 0,
    // Catálogo de ingredientes, cargado de forma perezosa mientras el editor de ítem está abierto:
    // autocompleta el nombre.
    val catalog: List<CatalogIngredient> = emptyList(),
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
    SaveTimeout,
    ItemAdded,
    ItemUpdated,
    ItemMarked,
    ItemUnmarked,
    ItemDeleted,
    PurchasedCleared,
    IngredientCreated,
    IngredientExists,
}
