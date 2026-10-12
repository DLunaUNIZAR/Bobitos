package com.dlunaunizar.bobitos.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeStepsTest {
    @Test
    fun `clean steps are trimmed and blank ones dropped keeping the order`() {
        assertEquals(
            listOf("Remoja las lentejas.", "Sofríe la verdura."),
            cleanRecipeSteps(listOf("  Remoja las lentejas. ", "", "   ", "Sofríe la verdura.")),
        )
    }

    @Test
    fun `the limits match the firestore rules`() {
        assertEquals(30, RecipeLimits.MAX_STEPS)
        assertEquals(500, RecipeLimits.MAX_STEP_LENGTH)
        assertEquals(1..50, RecipeLimits.SERVINGS)
        assertEquals(1..1440, RecipeLimits.TOTAL_MINUTES)
    }
}
