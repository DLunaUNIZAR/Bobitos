package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.core.common.SaveState
import com.dlunaunizar.bobitos.core.designsystem.component.ScreenFeedback
import com.dlunaunizar.bobitos.core.designsystem.component.screenFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// La pantalla cubre el aviso mientras «Has cocinado …» está abierto (covered = cooked != null): su error se ve dentro.
class MealsSnackbarErrorTest {
    private val review = CookedReview(mealName = "Tortilla", crossOff = emptyList(), finished = emptyList())

    @Test
    fun anOpenCookedDialogKeepsItsErrorOutOfTheSnackbar() {
        val state = MealsUiState(
            cooked = review,
            save = SaveState(error = MealUiMessage.CookedShoppingChanged),
            changedItemName = "Leche",
        )
        assertNull(state.save.screenFeedback(covered = state.cooked != null))
    }

    @Test
    fun withoutTheCookedDialogTheErrorGoesToTheSnackbar() {
        val state = MealsUiState(save = SaveState(error = MealUiMessage.CookedShoppingChanged))
        assertEquals(
            ScreenFeedback.Error(MealUiMessage.CookedShoppingChanged),
            state.save.screenFeedback(covered = state.cooked != null),
        )
    }
}
