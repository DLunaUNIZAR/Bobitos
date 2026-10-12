package com.dlunaunizar.bobitos.feature.recipes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipesValidationTest {
    @Test
    fun `title is required`() {
        assertEquals(
            RecipeUiMessage.TitleRequired,
            RecipesValidation.validate("   ", null, null),
        )
    }

    @Test
    fun `all field limits are validated`() {
        assertEquals(
            RecipeUiMessage.TitleTooLong,
            RecipesValidation.validate("a".repeat(RecipesValidation.MAX_TITLE_LENGTH + 1), null, null),
        )
        assertEquals(
            RecipeUiMessage.DescriptionTooLong,
            RecipesValidation.validate(
                "Lentejas",
                "a".repeat(RecipesValidation.MAX_DESCRIPTION_LENGTH + 1),
                null,
            ),
        )
        assertEquals(
            RecipeUiMessage.CategoryTooLong,
            RecipesValidation.validate("Lentejas", null, "a".repeat(RecipesValidation.MAX_CATEGORY_LENGTH + 1)),
        )
    }

    @Test
    fun `a valid recipe passes validation`() {
        assertNull(RecipesValidation.validate("Lentejas", "Con chorizo", "Legumbres"))
    }

    @Test
    fun `steps, servings and minutes are validated after the text fields`() {
        assertEquals(
            RecipeUiMessage.TooManySteps,
            RecipesValidation.validate("Lentejas", null, null, steps = List(31) { "Paso" }),
        )
        assertEquals(
            RecipeUiMessage.StepTooLong,
            RecipesValidation.validate("Lentejas", null, null, steps = listOf("a".repeat(501))),
        )
        assertEquals(RecipeUiMessage.ServingsInvalid, RecipesValidation.validate("Lentejas", null, null, servings = 0))
        assertEquals(RecipeUiMessage.ServingsInvalid, RecipesValidation.validate("Lentejas", null, null, servings = 51))
        assertEquals(
            RecipeUiMessage.TotalMinutesInvalid,
            RecipesValidation.validate("Lentejas", null, null, totalMinutes = 1441),
        )
        assertEquals(RecipeUiMessage.TitleRequired, RecipesValidation.validate(" ", null, null, servings = 0))
    }

    @Test
    fun `blank steps do not count and the limits themselves are valid`() {
        assertNull(
            RecipesValidation.validate(
                title = "Lentejas",
                description = null,
                category = null,
                steps = List(30) { "a".repeat(500) } + listOf("", "   "),
                servings = 50,
                totalMinutes = 1440,
            ),
        )
        assertNull(
            RecipesValidation.validate(
                title = "Lentejas",
                description = null,
                category = null,
                steps = listOf("  ${"a".repeat(500)}  "),
                servings = 1,
                totalMinutes = 1,
            ),
        )
    }
}
