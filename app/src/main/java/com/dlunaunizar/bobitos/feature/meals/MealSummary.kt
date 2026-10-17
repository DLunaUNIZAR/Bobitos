package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.Recipe

// Resumen de una comida para su tarjeta: cuántos ingredientes (null si no hay receta o no tiene) y quién come.
internal data class MealSummary(val ingredientCount: Int?, val participantNames: List<String>)

internal fun mealSummary(meal: Meal, recipe: Recipe?): MealSummary = MealSummary(
    ingredientCount = recipe?.ingredients?.size?.takeIf { it > 0 },
    participantNames = meal.participantNames,
)
