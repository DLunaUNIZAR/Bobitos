package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.core.model.pantryId
import com.dlunaunizar.bobitos.feature.common.PantryLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecipePickerTest {
    private val recipes = listOf(
        recipe("g1", RecipeVisibility.GLOBAL, "Lentejas estofadas", "Legumbres"),
        recipe("p2", RecipeVisibility.PRIVATE, "Tortilla de patatas", "Huevos"),
        recipe("g2", RecipeVisibility.GLOBAL, "Crema de calabacín", "Sopas y cremas"),
        recipe("p1", RecipeVisibility.PRIVATE, "Arroz con verduras", null),
    )

    @Test
    fun `mine come first and then the common ones, each by title`() {
        assertEquals(listOf("p1", "p2", "g2", "g1"), recipes.forPicker("").map(Recipe::id))
        assertEquals(listOf("p1", "p2", "g2", "g1"), recipes.forPicker("   ").map(Recipe::id))
    }

    @Test
    fun `the search looks at title and category ignoring accents and case`() {
        assertEquals(listOf("g2"), recipes.forPicker("calabacin").map(Recipe::id))
        assertEquals(listOf("g1"), recipes.forPicker("LEGUMBRES").map(Recipe::id))
        assertEquals(listOf("p2"), recipes.forPicker("huevos tortilla").map(Recipe::id))
        assertEquals(emptyList<String>(), recipes.forPicker("paella").map(Recipe::id))
    }

    @Test
    fun `each row is labelled as mine or common`() {
        assertEquals(R.string.meals_recipe_origin_mine, recipeOriginRes(recipes[1]))
        assertEquals(R.string.meals_recipe_origin_common, recipeOriginRes(recipes[0]))
    }

    @Test
    fun `without the pantry chip the picker keeps mine first and has no lines`() {
        val rows = recipes.pickerRows("", null)

        assertEquals(listOf("p1", "p2", "g2", "g1"), rows.map { it.recipe.id })
        assertTrue(rows.all { it.pantryLine == null })
    }

    @Test
    fun `with the pantry chip the picker orders by what is missing and adds the line`() {
        val withIngredients = listOf(
            recipe("p1", RecipeVisibility.PRIVATE, "Tortilla", null, listOf("Huevos", "Patatas")),
            recipe("g1", RecipeVisibility.GLOBAL, "Aliño", null, listOf("Sal")),
            recipe("p2", RecipeVisibility.PRIVATE, "Tostada", null),
        )
        val pantry = setOf(pantryId("Sal"), pantryId("Huevos"))

        val rows = withIngredients.pickerRows("", pantry)

        assertEquals(listOf("g1", "p1"), rows.map { it.recipe.id })
        assertEquals(PantryLine(R.string.recipes_pantry_all), rows[0].pantryLine)
        assertEquals(PantryLine(R.string.recipes_pantry_missing_one, listOf("patatas")), rows[1].pantryLine)
        assertEquals(listOf("p1"), withIngredients.pickerRows("tortilla", pantry).map { it.recipe.id })
    }

    private fun recipe(
        id: String,
        visibility: RecipeVisibility,
        title: String,
        category: String?,
        ingredients: List<String> = emptyList(),
    ) = Recipe(
        id = id,
        ownerUid = "u",
        visibility = visibility,
        title = title,
        description = null,
        category = category,
        ingredients = ingredients.map { Ingredient(it) }.takeIf { it.isNotEmpty() },
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
    )
}
