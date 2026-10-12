package com.dlunaunizar.bobitos.feature.recipes

import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility

// «Mis recetas» toma solo las privadas del usuario: las GLOBAL (también las que publica el admin del
// catálogo, que es su dueño) llegan siempre por la lista de comunes.
internal fun List<Recipe>.personalOnly(): List<Recipe> = filter { it.visibility == RecipeVisibility.PRIVATE }

// Acciones del detalle. Una privada siempre es del usuario (las reglas no dejan leer las ajenas): se edita y
// se borra. Una común se guarda como mía (también el admin) y el admin, además, la edita y la borra.
internal data class RecipeDetailActions(val canEdit: Boolean, val canFork: Boolean)

internal fun recipeDetailActions(recipe: Recipe, canWrite: Boolean, isAdmin: Boolean): RecipeDetailActions {
    val common = recipe.visibility == RecipeVisibility.GLOBAL
    return RecipeDetailActions(canEdit = canWrite && (!common || isAdmin), canFork = canWrite && common)
}
