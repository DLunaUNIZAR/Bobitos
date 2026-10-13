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
 * notas, la tienda y la marca con que «Sumar cantidad» rellena las notas vacías y, como pareja, la tienda y la marca
 * (ver [ShoppingDuplicate.storeAndBrandAfterSum]).
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

    /**
     * La cantidad del artículo para el aviso; null si no tiene. Solo el espacio entre el número inicial y la palabra que
     * le sigue no parte línea («5 ud»): una cantidad larga se parte por sus otros espacios, nunca a mitad de palabra.
     */
    val existingQuantityLabel: String?
        get() = existing.quantity?.trim()?.takeIf(String::isNotEmpty)?.replaceFirst(NUMBER_THEN_SPACE, "\$1 ")

    /**
     * Tienda y marca tras «Sumar cantidad»: lo tecleado rellena las del artículo como pareja, para no juntar una tienda
     * y una marca que no van juntas. Sin tienda ni marca, entran las tecleadas; con solo la tienda, la marca tecleada
     * entra si se tecleó esa misma tienda; con solo la marca, la tienda tecleada entra si se tecleó esa misma marca (sin
     * distinguir mayúsculas); con las dos, se conservan. Una marca solo con espacios cuenta como vacía.
     */
    fun storeAndBrandAfterSum(): Pair<Supermarket?, String?> {
        val store = existing.supermarket
        val listedBrand = existing.brand?.trim()?.takeIf(String::isNotEmpty)
        val typedBrand = brand?.trim()?.takeIf(String::isNotEmpty)
        return when {
            store == null && listedBrand == null -> supermarket to typedBrand
            listedBrand == null -> store to typedBrand.takeIf { supermarket == store }
            store == null -> supermarket.takeIf { listedBrand.equals(typedBrand, ignoreCase = true) } to listedBrand
            else -> store to listedBrand
        }
    }
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

// Número inicial («5», «1/2», «1,5») y el espacio que lo separa de la palabra siguiente.
private val NUMBER_THEN_SPACE = Regex("""^(\S*\d\S*)\s+""")
