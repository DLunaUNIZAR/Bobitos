package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.Nutrition
import kotlinx.coroutines.flow.Flow

/**
 * Marcas (con valores nutricionales) de un ingrediente del catálogo. Colaborativas: cualquier usuario
 * verificado añade marcas; solo su autor o un admin las edita/borra.
 */
interface IngredientBrandRepository {
    fun brands(ingredientId: String): Flow<List<IngredientBrand>>

    suspend fun addBrand(ingredientId: String, name: String, barcode: String?, nutrition: Nutrition?)

    suspend fun updateBrand(
        ingredientId: String,
        brandId: String,
        name: String,
        barcode: String?,
        nutrition: Nutrition?,
    )

    suspend fun deleteBrand(ingredientId: String, brandId: String)
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
