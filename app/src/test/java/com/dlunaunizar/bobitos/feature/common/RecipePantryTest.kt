package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.core.model.pantryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecipePantryTest {
    private val pantry = setOf(pantryId("Sal"), pantryId("Aceite de oliva"))

    @Test
    fun `what is missing keeps the recipe order and names, once per key`() {
        val tortilla = recipe("r1", "Tortilla", "Huevos", "Patatas", "huevo", "Sal", "  Cebolla ")

        assertEquals(listOf("Huevos", "Patatas", "Cebolla"), missingFromPantry(tortilla, pantry))
    }

    @Test
    fun `names without letters or numbers are neither in the pantry nor missing`() {
        val soup = recipe("r1", "Sopa", "—", "¡!", "  ", "Agua")

        assertEquals(listOf("Agua"), missingFromPantry(soup, pantry + ""))
    }

    @Test
    fun `with the pantry only recipes with ingredients show, from fewest missing, then by title`() {
        val recipes = listOf(
            recipe("r1", "Tortilla", "Huevos", "Patatas", "Sal"),
            recipe("r2", "Aliño", "Sal", "Aceite de oliva"),
            recipe("r3", "Sopa", "—"),
            recipe("r4", "Bocadillo"),
            recipe("r5", "Ensalada", "Lechuga", "Sal"),
            recipe("r6", "Arroz blanco", "Arroz", "Ajo"),
        )

        val rows = recipes.withPantryLines(pantry)

        assertEquals(listOf("r2", "r5", "r6", "r1"), rows.map { it.recipe.id })
        assertEquals(PantryLine(R.string.recipes_pantry_all), rows[0].pantryLine)
        assertEquals(PantryLine(R.string.recipes_pantry_missing_one, listOf("lechuga")), rows[1].pantryLine)
    }

    @Test
    fun `without the pantry the recipes stay as they are and have no line`() {
        val recipes = listOf(recipe("r1", "Tortilla", "Huevos"), recipe("r4", "Bocadillo"))

        assertEquals(listOf(RecipeRow(recipes[0], null), RecipeRow(recipes[1], null)), recipes.withPantryLines(null))
    }

    @Test
    fun `the line names up to three and counts the rest`() {
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_two, listOf("huevos", "leche")),
            pantryLine(listOf("Huevos", "Leche")),
        )
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_three, listOf("huevos", "leche", "harina")),
            pantryLine(listOf("Huevos", "Leche", "Harina")),
        )
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_more, listOf(5, "huevos", "leche", 3)),
            pantryLine(listOf("Huevos", "Leche", "Harina", "Azúcar", "Sal")),
        )
    }

    @Test
    fun `the line lowercases the initial but keeps acronyms`() {
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_two, listOf("ácido cítrico", "AOVE")),
            pantryLine(listOf("Ácido cítrico", "AOVE")),
        )
    }

    @Test
    fun `before the sound i the last conjunction is e`() {
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_two_e, listOf("sal", "higos")),
            pantryLine(listOf("Sal", "Higos")),
        )
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_three_e, listOf("huevos", "leche", "ibérico")),
            pantryLine(listOf("Huevos", "Leche", "Ibérico")),
        )
    }

    @Test
    fun `before a diphthong or any other sound the conjunction stays y`() {
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_two, listOf("agua", "hielo")),
            pantryLine(listOf("Agua", "Hielo")),
        )
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_three, listOf("sal", "pan", "hierbabuena")),
            pantryLine(listOf("Sal", "Pan", "Hierbabuena")),
        )
        assertEquals(
            PantryLine(R.string.recipes_pantry_missing_more, listOf(4, "sal", "pan", 2)),
            pantryLine(listOf("Sal", "Pan", "Higos", "Hinojo")),
        )
    }

    @Test
    fun `takesE follows the sound, not the spelling`() {
        listOf("higos", "Hinojo", "ibérico", "Ídolo", "hígado", "IPA", "hi").forEach { assertTrue(it, takesE(it)) }
        listOf("hielo", "hierbabuena", "hierro", "yogur", "huevos", "leche", "", "  ").forEach {
            assertFalse(it, takesE(it))
        }
    }

    private fun recipe(id: String, title: String, vararg names: String) = Recipe(
        id = id,
        ownerUid = "u",
        visibility = RecipeVisibility.PRIVATE,
        title = title,
        description = null,
        category = null,
        ingredients = names.map { Ingredient(it) }.takeIf { it.isNotEmpty() },
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
    )
}
