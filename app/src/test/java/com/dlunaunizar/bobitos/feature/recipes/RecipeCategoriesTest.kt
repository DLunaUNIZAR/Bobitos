package com.dlunaunizar.bobitos.feature.recipes

import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecipeCategoriesTest {
    @Test
    fun filtrarPorCategoriaIgnoraMayusculasYEspacios() {
        assertTrue(recipe(" Postres ").inCategory("postres"))
        assertFalse(recipe("Bebidas").inCategory("postres"))
        assertFalse(recipe(null).inCategory("postres"))
    }

    @Test
    fun sinCategoriaElegidaPasanTodas() {
        assertTrue(recipe("Postres").inCategory(null))
        assertTrue(recipe(null).inCategory(null))
    }

    @Test
    fun lasCategoriasSeJuntanYSeOrdenan() {
        val categories = listOf(
            recipe("Postres"),
            recipe(" postres"),
            recipe("Ácidos"),
            recipe(null),
        ).recipeCategories()

        assertEquals(listOf("Ácidos", "Postres"), categories)
    }

    private fun recipe(category: String?) = Recipe(
        id = "r1",
        ownerUid = "u",
        visibility = RecipeVisibility.GLOBAL,
        title = "Receta",
        description = null,
        category = category,
        createdBy = "u1",
        createdByName = "Ana",
        createdAt = Instant.parse("2026-10-17T10:00:00Z"),
        updatedBy = "u1",
        updatedAt = Instant.parse("2026-10-17T10:00:00Z"),
    )
}
