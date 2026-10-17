package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.IngredientBrand
import kotlinx.coroutines.flow.Flow

/**
 * Marcas personales (con valores nutricionales y tiendas) de los ingredientes del usuario activo,
 * guardadas en `users/{uid}/brands`. Cada usuario solo ve y escribe las suyas.
 */
interface IngredientBrandRepository {
    /** Todas las marcas del usuario (de todos los ingredientes). Vacío si no hay sesión. */
    fun brands(): Flow<List<IngredientBrand>>

    /** Crea la marca y devuelve su id. */
    suspend fun addBrand(input: BrandInput): String

    /** Edita la marca; `ingredientId` no cambia nunca. */
    suspend fun updateBrand(brandId: String, input: BrandInput)

    suspend fun setFavorite(brandId: String, favorite: Boolean)

    suspend fun deleteBrand(brandId: String)

    /** «Deshacer» el borrado: vuelve a escribir la marca con su id original. */
    suspend fun restoreBrand(brand: IngredientBrand)
}

enum class BrandFailure {
    NameRequired,
    NameTooLong,
    BarcodeTooLong,
    NotAuthenticated,
    EmailNotVerified,
    BrandNotFound,
    PermissionDenied,
    Network,
    Unknown,
}

class BrandRepositoryException(val failure: BrandFailure, cause: Throwable? = null) : Exception(cause)
