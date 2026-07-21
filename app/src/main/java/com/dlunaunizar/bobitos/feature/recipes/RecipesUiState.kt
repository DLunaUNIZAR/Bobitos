package com.dlunaunizar.bobitos.feature.recipes

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Recipe

data class RecipesUiState(
    val query: String = "",
    val global: UiState<List<Recipe>> = UiState.Loading,
    val mine: UiState<List<Recipe>> = UiState.Loading,
    val isSaving: Boolean = false,
    val error: RecipeUiMessage? = null,
    val notice: RecipeUiMessage? = null,
    // La cuenta activa puede publicar recetas en el catálogo común (GLOBAL).
    val isAdmin: Boolean = false,
)
