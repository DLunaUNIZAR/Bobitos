package com.dlunaunizar.bobitos.feature.shopping

import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.ShoppingQuantity
import com.dlunaunizar.bobitos.core.model.Supermarket
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

/**
 * Artículo ya pendiente en la lista ([existing]) y lo tecleado en el editor: la cantidad que se quería añadir y las
 * notas, la tienda y la marca con que «Sumar cantidad» rellena las que el artículo tenga vacías.
 */
data class ShoppingDuplicate(
    val existing: ShoppingItem,
    val quantity: String?,
    val notes: String? = null,
    val supermarket: Supermarket? = null,
    val brand: String? = null,
) {
    /** La cantidad del artículo tras «Sumar cantidad». */
    val summed: String? get() = ShoppingQuantity.sum(existing.quantity, quantity)

    /** La suma no cabe en una cantidad: el aviso lo dice y deshabilita «Sumar cantidad». */
    val sumTooLong: Boolean get() = (summed?.length ?: 0) > ShoppingQuantity.MAX_LENGTH

    /** La cantidad del artículo para el aviso, con espacios que no parten línea; null si no tiene. */
    val existingQuantityLabel: String?
        get() = existing.quantity?.trim()?.takeIf(String::isNotEmpty)?.replace(' ', ' ')
}

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
    ItemChanged,
    ListNotLoaded,
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
