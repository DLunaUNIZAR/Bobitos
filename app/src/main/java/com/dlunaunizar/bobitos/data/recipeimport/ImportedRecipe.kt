package com.dlunaunizar.bobitos.data.recipeimport

import com.dlunaunizar.bobitos.core.model.Ingredient

/**
 * Borrador de receta extraído de una web (schema.org/Recipe). No se persiste directamente: alimenta
 * el editor del recetario para que el usuario lo revise antes de guardar. Los campos ya llegan
 * recortados a los límites del modelo/reglas para que el editor los acepte sin retoques.
 */
data class ImportedRecipe(
    val title: String,
    val description: String?,
    val category: String?,
    val ingredients: List<Ingredient>,
    val sourceUrl: String,
)
