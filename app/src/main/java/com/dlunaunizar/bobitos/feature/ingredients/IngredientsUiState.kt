package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientPref

data class IngredientsUiState(
    val query: String = "",
    val catalog: UiState<List<CatalogIngredient>> = UiState.Loading,
    // Preferencias del usuario activo (ingredientId → super/marca por defecto).
    val prefs: Map<String, IngredientPref> = emptyMap(),
    // Cuenta activa: puede curar (editar/borrar) fichas ajenas.
    val isAdmin: Boolean = false,
    val currentUid: String? = null,
    val isSaving: Boolean = false,
    val error: IngredientUiMessage? = null,
    val notice: IngredientUiMessage? = null,
) {
    fun canEdit(ingredient: CatalogIngredient): Boolean = isAdmin || ingredient.ownerUid == currentUid
}
