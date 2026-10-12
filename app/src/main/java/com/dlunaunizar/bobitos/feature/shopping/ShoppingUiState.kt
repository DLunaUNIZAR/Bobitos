package com.dlunaunizar.bobitos.feature.shopping

import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingData

data class ShoppingUiState(
    val items: UiState<List<ShoppingItem>> = UiState.Loading,
    val isSaving: Boolean = false,
    val writeStatus: ShoppingWriteStatus = ShoppingWriteStatus.IDLE,
    val editorSave: EditorSaveStatus = EditorSaveStatus.IDLE,
    val error: ShoppingUiMessage? = null,
    val notice: ShoppingUiMessage? = null,
    val lastClearedCount: Int = 0,
    // Ingredientes, marcas y preferencias, cargados de forma perezosa mientras el editor de ítem está
    // abierto: autocompletan el nombre y proponen marca y tienda.
    val ingredientData: IngredientShoppingData = IngredientShoppingData(),
    // Aviso de artículo ya pendiente al crear uno («Sumar cantidad» / «No añadir»); null si no hay.
    val duplicate: ShoppingDuplicate? = null,
)

/** Artículo ya pendiente en la lista ([existing]) y la cantidad que se quería añadir. */
data class ShoppingDuplicate(val existing: ShoppingItem, val quantity: String?)

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
