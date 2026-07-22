package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref

data class IngredientDetailUiState(
    val ingredientId: String = "",
    val ingredient: CatalogIngredient? = null,
    // El catálogo ya emitió (para distinguir «cargando» de «no existe»).
    val loaded: Boolean = false,
    val brands: List<IngredientBrand> = emptyList(),
    val pref: IngredientPref? = null,
    val isAdmin: Boolean = false,
    val currentUid: String? = null,
    val isSaving: Boolean = false,
    val error: IngredientUiMessage? = null,
    val notice: IngredientUiMessage? = null,
    // El ingrediente se borró: la pantalla debe cerrarse.
    val finished: Boolean = false,
) {
    val canEditIngredient: Boolean
        get() = ingredient != null && (isAdmin || ingredient.ownerUid == currentUid)

    fun canEditBrand(brand: IngredientBrand): Boolean = isAdmin || brand.ownerUid == currentUid
}
