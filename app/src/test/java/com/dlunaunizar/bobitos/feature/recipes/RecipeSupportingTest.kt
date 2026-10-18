package com.dlunaunizar.bobitos.feature.recipes

import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.feature.common.PantryLine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RecipeSupportingTest {
    private val pantryLine = PantryLine(R.string.recipes_pantry_all)

    @Test
    fun conDespensaSaleLaLineaDeLaDespensa() {
        val recipe = recipe(category = "Pasta", totalMinutes = 25, servings = 4)

        assertEquals(
            RecipeSupporting.Pantry(pantryLine),
            recipeSupporting(recipe, pantryLine, showCategory = true),
        )
    }

    @Test
    fun alBuscarSaleLaCategoria() {
        val recipe = recipe(category = "Pasta", totalMinutes = 25, servings = 4)

        assertEquals(RecipeSupporting.Category("Pasta"), recipeSupporting(recipe, null, showCategory = true))
    }

    @Test
    fun sinBuscarSalenMinutosYRaciones() {
        val recipe = recipe(category = "Pasta", totalMinutes = 25, servings = 4)

        assertEquals(RecipeSupporting.Meta(25, 4), recipeSupporting(recipe, null, showCategory = false))
        assertEquals(RecipeSupporting.Meta(null, 2), recipeSupporting(recipe(servings = 2), null, showCategory = true))
    }

    @Test
    fun sinDatosNoHayLinea() {
        assertEquals(RecipeSupporting.None, recipeSupporting(recipe(), null, showCategory = false))
        assertEquals(RecipeSupporting.None, recipeSupporting(recipe(category = " "), null, showCategory = true))
    }

    private fun recipe(category: String? = null, totalMinutes: Int? = null, servings: Int? = null) = Recipe(
        id = "r1",
        ownerUid = "u",
        visibility = RecipeVisibility.GLOBAL,
        title = "Receta",
        description = null,
        category = category,
        servings = servings,
        totalMinutes = totalMinutes,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
    )
}
