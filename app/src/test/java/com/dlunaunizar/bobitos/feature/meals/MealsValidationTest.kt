package com.dlunaunizar.bobitos.feature.meals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MealsValidationTest {
    @Test
    fun `name is required`() {
        assertEquals(
            MealUiMessage.NameRequired,
            MealsValidation.validate("   "),
        )
    }

    @Test
    fun `name over the limit is rejected`() {
        assertEquals(
            MealUiMessage.NameTooLong,
            MealsValidation.validate("a".repeat(MealsValidation.MAX_NAME_LENGTH + 1)),
        )
    }

    @Test
    fun `name at the limit is valid`() {
        assertNull(MealsValidation.validate("a".repeat(MealsValidation.MAX_NAME_LENGTH)))
    }

    @Test
    fun `a normal name passes validation`() {
        assertNull(MealsValidation.validate("Lentejas"))
    }
}
