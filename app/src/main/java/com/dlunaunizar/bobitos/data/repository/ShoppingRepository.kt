package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.ShoppingItem
import kotlinx.coroutines.flow.Flow

interface ShoppingRepository {
    fun items(spaceId: String): Flow<List<ShoppingItem>>

    suspend fun addItem(spaceId: String, name: String, quantity: String?, notes: String?)

    suspend fun updateItem(spaceId: String, itemId: String, name: String, quantity: String?, notes: String?)

    suspend fun setPurchased(spaceId: String, itemId: String, purchased: Boolean)

    suspend fun deleteItem(spaceId: String, itemId: String)

    suspend fun clearPurchased(spaceId: String): Int
}

enum class ShoppingFailure {
    NameRequired,
    NameTooLong,
    QuantityTooLong,
    NotesTooLong,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    ItemNotFound,
    PermissionDenied,
    Network,
    Unknown,
}

class ShoppingRepositoryException(val failure: ShoppingFailure, cause: Throwable? = null) : Exception(cause)
