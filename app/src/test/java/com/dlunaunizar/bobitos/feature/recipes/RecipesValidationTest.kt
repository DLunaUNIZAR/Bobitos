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
}
