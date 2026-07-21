package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.SpaceMember
import java.time.LocalDate
import java.time.temporal.ChronoField

data class MealsUiState(
    val focusedDate: LocalDate = LocalDate.now(),
    val meals: UiState<List<Meal>> = UiState.Loading,
    val members: UiState<List<SpaceMember>> = UiState.Loading,
    val isSaving: Boolean = false,
    val error: MealUiMessage? = null,
    val notice: MealUiMessage? = null,
    val recipes: List<Recipe> = emptyList(),
) {
    // Lunes de la semana del día enfocado (ISO: día 1 = lunes). Define la ventana observada.
    val weekStart: LocalDate get() = focusedDate.with(ChronoField.DAY_OF_WEEK, 1L)

    // Los 7 días (lunes→domingo) de la semana enfocada, para la tira selectora.
    val weekDays: List<LocalDate> get() = (0L until DAYS_IN_WEEK).map(weekStart::plusDays)

    private companion object {
        const val DAYS_IN_WEEK = 7L
    }
}
