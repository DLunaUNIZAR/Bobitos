package com.dlunaunizar.bobitos.feature.recipes

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.data.recipeimport.ImportedRecipe
import com.dlunaunizar.bobitos.feature.common.IngredientReviewRow

data class RecipesUiState(
    val query: String = "",
    val global: UiState<List<Recipe>> = UiState.Loading,
    val mine: UiState<List<Recipe>> = UiState.Loading,
    val isSaving: Boolean = false,
    val error: RecipeUiMessage? = null,
    val notice: RecipeUiMessage? = null,
    // La cuenta activa puede publicar recetas en el catálogo común (GLOBAL).
    val isAdmin: Boolean = false,
    // Importación desde una web: descarga en curso y borrador pendiente de abrir en el editor.
    val isImporting: Boolean = false,
    val importDraft: ImportedRecipe? = null,
    // Revisión de cantidades al añadir los ingredientes de una receta a la Compra (null = sin diálogo).
    val ingredientReview: List<IngredientReviewRow>? = null,
)
