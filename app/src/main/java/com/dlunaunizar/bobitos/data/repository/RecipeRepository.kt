package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import kotlinx.coroutines.flow.Flow

interface RecipeRepository {
    fun globalRecipes(): Flow<List<Recipe>>

    fun myRecipes(): Flow<List<Recipe>>

    suspend fun createRecipe(visibility: RecipeVisibility, title: String, description: String?, category: String?)

    suspend fun updateRecipe(recipeId: String, title: String, description: String?, category: String?)

    suspend fun deleteRecipe(recipeId: String)
}

enum class RecipeFailure {
    TitleRequired,
    TitleTooLong,
    DescriptionTooLong,
    CategoryTooLong,
    NotAuthenticated,
    EmailNotVerified,
    RecipeNotFound,
    PermissionDenied,
    Network,
    Unknown,
}

class RecipeRepositoryException(val failure: RecipeFailure, cause: Throwable? = null) : Exception(cause)
