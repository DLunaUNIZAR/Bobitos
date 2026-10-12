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

    @Test
    fun editarCargaPasosRacionesYTiempo() {
        val draft = RecipeDraft.of(
            recipe().copy(steps = listOf("Remoja.", "Cuece."), servings = 4, totalMinutes = 40),
            null,
        )
        assertEquals(listOf("Remoja.", "Cuece."), draft.steps)
        assertEquals("4", draft.servings)
        assertEquals("40", draft.totalMinutes)
    }

    @Test
    fun unaRecetaAntiguaSinPasosAbreConLosCamposVaciosYSinError() {
        val draft = RecipeDraft.of(recipe(), null)
        assertEquals(emptyList<String>(), draft.steps)
        assertEquals("", draft.servings)
        assertEquals("", draft.totalMinutes)
        assertNull(draft.validate())
    }

    @Test
    fun unBorradorImportadoTraePasosRacionesYTiempo() {
        val imported = ImportedRecipe(
            title = "Gazpacho",
            description = null,
            category = null,
            ingredients = emptyList(),
            sourceUrl = "https://web.example/gazpacho",
            steps = listOf("Tritura."),
            servings = 6,
            totalMinutes = 15,
        )
        val draft = RecipeDraft.of(null, imported)
        assertEquals(listOf("Tritura."), draft.steps)
        assertEquals("6", draft.servings)
        assertEquals("15", draft.totalMinutes)
    }

    @Test
    fun losPasosSeSubenBajanCambianQuitanYAnadenHasta30() {
        val draft = RecipeDraft.of(null, null).copy(steps = listOf("A", "B", "C"))
        assertEquals(listOf("B", "A", "C"), draft.withStepMoved(1, -1).steps)
        assertEquals(listOf("A", "C", "B"), draft.withStepMoved(1, 1).steps)
        assertEquals(draft, draft.withStepMoved(0, -1))
        assertEquals(draft, draft.withStepMoved(2, 1))
        assertEquals(listOf("A", "C"), draft.withoutStep(1).steps)
        assertEquals(listOf("A", "Bis", "C"), draft.withStep(1, "Bis").steps)
        assertEquals(listOf("A", "B", "C", ""), draft.withNewStep().steps)
        val full = draft.copy(steps = List(30) { "Paso $it" })
        assertEquals(full, full.withNewStep())
    }

    @Test
    fun racionesYTiempoSoloAceptanEnterosEnRango() {
        assertNull("".toRecipeCount())
        assertNull("   ".toRecipeCount())
        assertEquals(4, " 4 ".toRecipeCount())
        assertEquals(0, "dos".toRecipeCount())
        assertEquals(0, "1,5".toRecipeCount())
        val draft = RecipeDraft.of(null, null).copy(title = "Lentejas")
        assertEquals(RecipeUiMessage.ServingsInvalid, draft.copy(servings = "dos").validate())
        assertEquals(RecipeUiMessage.ServingsInvalid, draft.copy(servings = "0").validate())
        assertEquals(RecipeUiMessage.TotalMinutesInvalid, draft.copy(totalMinutes = "1441").validate())
        assertEquals(RecipeUiMessage.TotalMinutesInvalid, draft.copy(totalMinutes = "1,5").validate())
        assertNull(draft.copy(servings = " 4 ", totalMinutes = "40").validate())
    }

    @Test
    fun losPasosEnBlancoNoCuentanYUnPasoLargoEsError() {
        val draft = RecipeDraft.of(null, null).copy(title = "Lentejas")
        assertNull(draft.copy(steps = listOf("", "  ", "Cuece.")).validate())
        assertEquals(RecipeUiMessage.StepTooLong, draft.copy(steps = listOf("a".repeat(501))).validate())
    }
}
