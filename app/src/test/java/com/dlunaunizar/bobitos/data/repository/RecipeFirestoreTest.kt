package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.RecipeLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeFirestoreTest {
    @Test
    fun `a missing author name stays empty instead of becoming the uid`() {
        assertEquals("", recipeAuthorName(null))
        assertEquals("", recipeAuthorName("   "))
        assertEquals("Ana", recipeAuthorName(" Ana "))
    }

    @Test
    fun `steps are read as their non blank texts and a missing field stays null`() {
        assertEquals(listOf("Remoja.", "Cuece."), parseRecipeSteps(listOf("Remoja.", "  ", 3, null, "Cuece.")))
        assertEquals(emptyList<String>(), parseRecipeSteps(emptyList<String>()))
        assertNull(parseRecipeSteps(null))
        assertNull(parseRecipeSteps("Remoja y cuece"))
    }

    @Test
    fun `servings and minutes are read only as whole numbers inside their range`() {
        assertEquals(4, parseRecipeCount(4L, RecipeLimits.SERVINGS))
        assertEquals(50, parseRecipeCount(50, RecipeLimits.SERVINGS))
        assertEquals(1440, parseRecipeCount(1440L, RecipeLimits.TOTAL_MINUTES))
        assertNull(parseRecipeCount(0L, RecipeLimits.SERVINGS))
        assertNull(parseRecipeCount(51L, RecipeLimits.SERVINGS))
        assertNull(parseRecipeCount(1441L, RecipeLimits.TOTAL_MINUTES))
        assertNull(parseRecipeCount(Long.MAX_VALUE, RecipeLimits.TOTAL_MINUTES))
        assertNull(parseRecipeCount(4.5, RecipeLimits.SERVINGS))
        assertNull(parseRecipeCount("4", RecipeLimits.SERVINGS))
        assertNull(parseRecipeCount(null, RecipeLimits.SERVINGS))
    }

    @Test
    fun `details inside the limits pass and outside them report the failure`() {
        val longest = "a".repeat(RecipeLimits.MAX_STEP_LENGTH)
        assertNull(recipeDetailsFailure(List(30) { longest }, 50, 1440))
        assertNull(recipeDetailsFailure(emptyList(), null, null))
        assertEquals(RecipeFailure.TooManySteps, recipeDetailsFailure(List(31) { "Paso" }, null, null))
        assertEquals(RecipeFailure.StepTooLong, recipeDetailsFailure(listOf(longest + "a"), null, null))
        assertEquals(RecipeFailure.ServingsOutOfRange, recipeDetailsFailure(emptyList(), 0, null))
        assertEquals(RecipeFailure.ServingsOutOfRange, recipeDetailsFailure(emptyList(), 51, null))
        assertEquals(RecipeFailure.TotalMinutesOutOfRange, recipeDetailsFailure(emptyList(), null, 0))
        assertEquals(RecipeFailure.TotalMinutesOutOfRange, recipeDetailsFailure(emptyList(), null, 1441))
    }
}
