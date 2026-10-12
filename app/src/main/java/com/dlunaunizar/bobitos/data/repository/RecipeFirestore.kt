package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.RecipeLimits

// Pasos, raciones y tiempo de una receta tal como se guardan: `steps` es una lista de textos (vacía si no
// hay) y `servings`/`totalMinutes`, enteros o null. El importador del catálogo (plan C) escribe lo mismo.

// El primer límite que no cumplen pasos (ya limpios), raciones o tiempo; null si todo cabe.
internal fun recipeDetailsFailure(steps: List<String>, servings: Int?, totalMinutes: Int?): RecipeFailure? = when {
    steps.size > RecipeLimits.MAX_STEPS -> RecipeFailure.TooManySteps
    steps.any { it.length > RecipeLimits.MAX_STEP_LENGTH } -> RecipeFailure.StepTooLong
    servings != null && servings !in RecipeLimits.SERVINGS -> RecipeFailure.ServingsOutOfRange
    totalMinutes != null && totalMinutes !in RecipeLimits.TOTAL_MINUTES -> RecipeFailure.TotalMinutesOutOfRange
    else -> null
}

// Retro-compat: campo ausente o que no es lista → null; lista → sus textos no vacíos.
internal fun parseRecipeSteps(raw: Any?): List<String>? =
    (raw as? List<*>)?.mapNotNull { (it as? String)?.takeIf(String::isNotBlank) }

// Raciones o minutos guardados: un entero dentro de [range]; cualquier otra cosa (ausente, decimal, texto,
// fuera de rango) → null. Firestore devuelve los enteros como Long.
internal fun parseRecipeCount(raw: Any?, range: IntRange): Int? {
    val value = when (raw) {
        is Long -> raw
        is Int -> raw.toLong()
        else -> return null
    }
    return value.takeIf { it >= range.first && it <= range.last }?.toInt()
}
