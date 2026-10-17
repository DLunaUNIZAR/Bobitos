package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import kotlinx.coroutines.flow.Flow

/**
 * Catálogo común de ingredientes, versionado y compartido por todos los usuarios. Solo el admin
 * lo escribe. La personalización por usuario (supermercado/marca por defecto) vive en [IngredientPrefsRepository], no aquí.
 */
interface IngredientRepository {
    fun catalog(): Flow<List<CatalogIngredient>>

    /** Si la cuenta activa puede escribir en el catálogo común. */
    fun isCurrentUserCatalogAdmin(): Boolean

    /** Uid de la cuenta activa, o null sin sesión. */
    fun currentUserId(): String?

    suspend fun createIngredient(name: String, category: String?, defaultUnit: String?)

    suspend fun updateIngredient(id: String, name: String, category: String?, defaultUnit: String?)

    suspend fun deleteIngredient(id: String)
}

enum class IngredientFailure {
    NameRequired,
    NameTooLong,
    CategoryTooLong,
    UnitTooLong,
    AlreadyExists,
    NotAuthenticated,
    EmailNotVerified,
    IngredientNotFound,
    PermissionDenied,
    Network,
    Unknown,
}

class IngredientRepositoryException(val failure: IngredientFailure, cause: Throwable? = null) : Exception(cause)
