package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import kotlinx.coroutines.flow.Flow

/**
 * Catálogo global de ingredientes compartido por todos los usuarios. Lo crea cualquier usuario
 * verificado; solo su autor (o un admin) puede editarlo/borrarlo. La personalización por usuario
 * (supermercado/marca por defecto) vive en [IngredientPrefsRepository], no aquí.
 */
interface IngredientRepository {
    fun catalog(): Flow<List<CatalogIngredient>>

    /** Si la cuenta activa puede curar (editar/borrar) fichas ajenas del catálogo común. */
    fun isCurrentUserCatalogAdmin(): Boolean

    /** UID de la cuenta activa, o null si no hay sesión (para saber qué fichas son propias). */
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
    NotAuthenticated,
    EmailNotVerified,
    IngredientNotFound,
    PermissionDenied,
    Network,
    Unknown,
}

class IngredientRepositoryException(val failure: IngredientFailure, cause: Throwable? = null) : Exception(cause)
