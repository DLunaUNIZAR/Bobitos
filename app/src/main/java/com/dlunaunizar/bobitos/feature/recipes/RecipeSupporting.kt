package com.dlunaunizar.bobitos.feature.recipes

import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.feature.common.PantryLine

/** La línea secundaria de una receta en el Recetario. */
sealed interface RecipeSupporting {
    data class Pantry(val line: PantryLine) : RecipeSupporting

    data class Meta(val minutes: Int?, val servings: Int?) : RecipeSupporting

    data class Category(val name: String) : RecipeSupporting

    data object None : RecipeSupporting
}

/**
 * Qué línea lleva [recipe] bajo el título: la de la despensa si hay [pantryLine]; la categoría si [showCategory] (lista
 * plana, sin cabeceras que la digan) y la receta la tiene; los minutos y las raciones si hay alguno; si no, ninguna.
 */
fun recipeSupporting(recipe: Recipe, pantryLine: PantryLine?, showCategory: Boolean): RecipeSupporting {
    val category = recipe.category?.trim()?.takeIf(String::isNotEmpty)
    return when {
        pantryLine != null -> RecipeSupporting.Pantry(pantryLine)
        showCategory && category != null -> RecipeSupporting.Category(category)
        recipe.totalMinutes != null || recipe.servings != null ->
            RecipeSupporting.Meta(recipe.totalMinutes, recipe.servings)
        else -> RecipeSupporting.None
    }
}
