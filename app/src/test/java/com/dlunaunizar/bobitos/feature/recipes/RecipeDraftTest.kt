package com.dlunaunizar.bobitos.feature.recipes

import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.data.recipeimport.ImportedRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RecipeDraftTest {
    private fun recipe(visibility: RecipeVisibility = RecipeVisibility.PRIVATE) = Recipe(
        id = "r1", ownerUid = "u", visibility = visibility, title = "Lentejas", description = null,
        category = "Legumbres", sourceUrl = "https://origen.example/lentejas",
        ingredients = listOf(Ingredient("Lentejas", "300", "g"), Ingredient("Sal")),
        createdBy = "u", createdByName = "U", createdAt = Instant.EPOCH, updatedBy = "u", updatedAt = Instant.EPOCH,
    )

    @Test
    fun unaRecetaNuevaEmpiezaVaciaYPrivada() {
        assertEquals(RecipeDraft("", "", "", false, emptyList(), null), RecipeDraft.of(null, null))
    }

    @Test
    fun editarCargaSusDatosYLosNulosSonVacios() {
        val draft = RecipeDraft.of(recipe(RecipeVisibility.GLOBAL), null)
        assertEquals("Lentejas", draft.title)
        assertEquals("", draft.description)
        assertEquals("Legumbres", draft.category)
        assertEquals(true, draft.global)
        assertEquals(listOf(IngredientRow("Lentejas", "300", "g"), IngredientRow("Sal", "", "")), draft.ingredients)
        // El enlace de origen solo viaja con un borrador importado, no al editar una receta existente.
        assertNull(draft.sourceUrl)
    }

    @Test
    fun unBorradorImportadoConservaSuEnlaceDeOrigen() {
        val imported = ImportedRecipe(
            title = "Tortilla",
            description = "con cebolla",
            category = null,
            ingredients = listOf(Ingredient("Huevos", "6", null)),
            sourceUrl = "https://web.example/tortilla",
        )
        val draft = RecipeDraft.of(null, imported)
        assertEquals("Tortilla", draft.title)
        assertEquals("con cebolla", draft.description)
        assertEquals("", draft.category)
        assertEquals(listOf(IngredientRow("Huevos", "6", "")), draft.ingredients)
        assertEquals("https://web.example/tortilla", draft.sourceUrl)
    }

    @Test
    fun alGuardarSeDescartanFilasSinNombreYSeNormalizaLoVacio() {
        val rows = listOf(IngredientRow("  Arroz ", " 200 ", ""), IngredientRow("   ", "5", "g"), IngredientRow("Sal"))
        assertEquals(listOf(Ingredient("Arroz", "200", null), Ingredient("Sal", null, null)), rows.toIngredients())
    }
}
