package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import kotlinx.coroutines.flow.Flow

interface ShoppingRepository {
    fun items(spaceId: String): Flow<List<ShoppingItem>>

    /**
     * Lectura puntual de la lista tal como está en el servidor, para la revisión de ingredientes: el primer
     * snapshot de [items] puede salir de la caché local (vieja) y la revisión se haría sobre datos caducados.
     */
    suspend fun currentItems(spaceId: String): List<ShoppingItem>

    suspend fun addItem(
        spaceId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    )

    suspend fun updateItem(
        spaceId: String,
        itemId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    )

    /**
     * Escribe [writes] en una sola transacción: o se aplican todas o ninguna. Cada [ShoppingWrite.Change] comprueba
     * antes que su artículo sigue como se vio ([ShoppingItem.sameContentAs]); si otro miembro lo ha cambiado, no se
     * escribe nada y falla con [ShoppingFailure.ItemChanged] (con [ShoppingFailure.ItemNotFound] si lo ha borrado).
     */
    suspend fun applyWrites(spaceId: String, writes: List<ShoppingWrite>)

    /**
     * Marca o desmarca [itemId] en una transacción. Al marcar con [pantryEntry] (el artículo es un ingrediente
     * reconocido), la apunta en la despensa si no estaba, con `shoppingItemId = itemId`. Al desmarcar, quita la
     * entrada de la despensa del nombre del artículo solo si la trajo esta compra (mismo `shoppingItemId`).
     */
    suspend fun setPurchased(spaceId: String, itemId: String, purchased: Boolean, pantryEntry: PantryEntry? = null)

    /**
     * «Listo» en «Has cocinado …», en una sola transacción (todo o nada): marca como comprados los artículos de
     * [crossOff] y apunta en la despensa su entrada, si la traen y no estaba (como [setPurchased]); quita de la
     * despensa [finishedPantryIds]; y escribe [restock] sobre la lista tal como se vio (como [applyWrites]). Un artículo
     * de [crossOff] que ya no existe o ya está comprado se salta. Si otro miembro ha cambiado o borrado un artículo de
     * [restock], no se escribe nada y falla con [ShoppingFailure.ItemChanged] o [ShoppingFailure.ItemNotFound].
     */
    suspend fun applyCooked(
        spaceId: String,
        crossOff: List<ShoppingCrossOff>,
        finishedPantryIds: List<String>,
        restock: List<ShoppingWrite>,
    )

    suspend fun deleteItem(spaceId: String, itemId: String)

    suspend fun clearPurchased(spaceId: String): Int
}

/** Una escritura de [ShoppingRepository.applyWrites]. */
sealed interface ShoppingWrite {
    /** Añade un artículo pendiente. */
    data class Add(
        val name: String,
        val quantity: String?,
        val notes: String?,
        val supermarket: Supermarket?,
        val brand: String?,
    ) : ShoppingWrite

    /**
     * Cambia cantidad, notas, tienda y marca de [seen], el artículo tal como se vio (su nombre no cambia). Con
     * [relist], además lo deja pendiente, sin quién ni cuándo lo compró.
     */
    data class Change(
        val seen: ShoppingItem,
        val quantity: String?,
        val notes: String?,
        val supermarket: Supermarket?,
        val brand: String?,
        val relist: Boolean,
    ) : ShoppingWrite
}

/** Un artículo que se tacha al cocinar y la entrada de la despensa que trae si es un ingrediente reconocido. */
data class ShoppingCrossOff(val itemId: String, val pantryEntry: PantryEntry?)

enum class ShoppingFailure {
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
    PermissionDenied,
    Network,
    Unknown,
}

class ShoppingRepositoryException(val failure: ShoppingFailure, cause: Throwable? = null) : Exception(cause)
