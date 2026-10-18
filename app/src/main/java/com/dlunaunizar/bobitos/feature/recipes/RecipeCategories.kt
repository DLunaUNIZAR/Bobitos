package com.dlunaunizar.bobitos.feature.recipes

import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.feature.common.categoryOptions
import com.dlunaunizar.bobitos.feature.common.sameCategoryAs

/** Las opciones del desplegable de categoría: sin repetidas ni vacías, ordenadas. */
internal fun List<Recipe>.recipeCategories(): List<String> = categoryOptions(Recipe::category)

/** Con [category] null pasa cualquier receta; si no, solo las de esa categoría, sin mayúsculas ni espacios en los extremos. */
internal fun Recipe.inCategory(category: String?): Boolean = category == null || this.category.sameCategoryAs(category)
