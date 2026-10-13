package com.dlunaunizar.bobitos.data.repository

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

    suspend fun setPurchased(spaceId: String, itemId: String, purchased: Boolean)

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
