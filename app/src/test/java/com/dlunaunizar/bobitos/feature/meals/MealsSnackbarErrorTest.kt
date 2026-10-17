package com.dlunaunizar.bobitos.feature.meals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MealsSnackbarErrorTest {
    private val review = CookedReview(mealName = "Tortilla", crossOff = emptyList(), finished = emptyList())

    @Test
    fun anOpenCookedDialogKeepsItsErrorOutOfTheSnackbar() {
        val state = MealsUiState(
            cooked = review,
            error = MealUiMessage.CookedShoppingChanged,
            changedItemName = "Leche",
        )
        assertNull(state.snackbarError())
    }

    @Test
    fun withoutTheCookedDialogTheErrorGoesToTheSnackbar() {
        val state = MealsUiState(error = MealUiMessage.CookedShoppingChanged)
        assertEquals(MealUiMessage.CookedShoppingChanged, state.snackbarError())
    }
}
