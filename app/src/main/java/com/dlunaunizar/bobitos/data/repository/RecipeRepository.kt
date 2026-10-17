package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import kotlinx.coroutines.flow.Flow

interface RecipeRepository {
    fun globalRecipes(): Flow<List<Recipe>>

    fun myRecipes(): Flow<List<Recipe>>

    /** Si la cuenta activa está en la allowlist que puede publicar recetas GLOBAL. */
    fun isCurrentUserRecipeAdmin(): Boolean

    suspend fun createRecipe(
        visibility: RecipeVisibility,
        title: String,
        description: String?,
        category: String?,
        sourceRecipeId: String?,
        ingredients: List<Ingredient> = emptyList(),
        sourceUrl: String? = null,
        steps: List<String> = emptyList(),
        servings: Int? = null,
        totalMinutes: Int? = null,
    ): String

    suspend fun updateRecipe(
        recipeId: String,
        title: String,
        description: String?,
        category: String?,
        ingredients: List<Ingredient> = emptyList(),
        steps: List<String> = emptyList(),
        servings: Int? = null,
        totalMinutes: Int? = null,
    )

    suspend fun deleteRecipe(recipeId: String)

    /**
     * Recrea con su mismo id una receta recién borrada («Deshacer»), con sus datos y su autor. No lleva `source`:
     * las reglas no dejan que un cliente lo escriba, así que una común del catálogo vuelve como común manual.
     */
    suspend fun restoreRecipe(recipe: Recipe)
}

enum class RecipeFailure {
    TitleRequired,
    TitleTooLong,
    DescriptionTooLong,
    CategoryTooLong,
    TooManySteps,
    StepTooLong,
    ServingsOutOfRange,
    TotalMinutesOutOfRange,
    NotAuthenticated,
    EmailNotVerified,
    RecipeNotFound,
    PermissionDenied,
    Network,
    Unknown,
}

class RecipeRepositoryException(val failure: RecipeFailure, cause: Throwable? = null) : Exception(cause)
