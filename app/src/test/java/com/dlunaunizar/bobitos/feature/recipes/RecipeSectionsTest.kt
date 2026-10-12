package com.dlunaunizar.bobitos.feature.recipes

import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RecipeSectionsTest {
    @Test
    fun `the search goes by words without accents or case in title, category and ingredients`() {
        val pollo = recipe("p1", RecipeVisibility.GLOBAL).copy(
            title = "Pechugas de pollo al limón",
            category = "Carnes",
            ingredients = listOf(Ingredient("Pechuga de pollo")),
        )
        val arroz = recipe("a1", RecipeVisibility.PRIVATE).copy(
            title = "Arroz al horno",
            category = "Arroces",
            ingredients = listOf(Ingredient("Garbanzos cocidos")),
        )
        val all = listOf(pollo, arroz)

        assertEquals(listOf("p1"), all.matching("limon").map(Recipe::id))
        assertEquals(listOf("p1"), all.matching("  POLLO   limón ").map(Recipe::id))
        assertEquals(listOf("a1"), all.matching("garbanzos").map(Recipe::id))
        assertEquals(listOf("a1"), all.matching("arroces").map(Recipe::id))
        assertEquals(emptyList<String>(), all.matching("limón arroces").map(Recipe::id))
        assertEquals(listOf("p1", "a1"), all.matching(" ").map(Recipe::id))
    }

    @Test
    fun `my recipes keep only the private ones`() {
        val mine = listOf(
            recipe("lentejas", RecipeVisibility.GLOBAL),
            recipe("p1", RecipeVisibility.PRIVATE),
            recipe("p2", RecipeVisibility.PRIVATE),
        )
        assertEquals(listOf("p1", "p2"), mine.personalOnly().map(Recipe::id))
    }

    @Test
    fun `a private recipe is edited and deleted but not forked`() {
        assertEquals(
            RecipeDetailActions(canEdit = true, canFork = false),
            recipeDetailActions(recipe("p1", RecipeVisibility.PRIVATE), canWrite = true, isAdmin = false),
        )
    }

    @Test
    fun `a common recipe is forked by anyone who can write and also edited by the admin`() {
        val common = recipe("lentejas", RecipeVisibility.GLOBAL)
        assertEquals(
            RecipeDetailActions(canEdit = false, canFork = true),
            recipeDetailActions(common, canWrite = true, isAdmin = false),
        )
        assertEquals(
            RecipeDetailActions(canEdit = true, canFork = true),
            recipeDetailActions(common, canWrite = true, isAdmin = true),
        )
    }

    @Test
    fun `without write access there are no actions`() {
        val none = RecipeDetailActions(canEdit = false, canFork = false)
        assertEquals(
            none,
            recipeDetailActions(recipe("p1", RecipeVisibility.PRIVATE), canWrite = false, isAdmin = true),
        )
        assertEquals(
            none,
            recipeDetailActions(recipe("lentejas", RecipeVisibility.GLOBAL), canWrite = false, isAdmin = true),
        )
    }

    private fun recipe(id: String, visibility: RecipeVisibility) = Recipe(
        id = id,
        ownerUid = "u",
        visibility = visibility,
        title = id,
        description = null,
        category = null,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
    )
}
