package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.feature.common.IngredientReviewRow
import java.time.LocalDate
import java.time.temporal.ChronoField

data class MealsUiState(
    val focusedDate: LocalDate = LocalDate.now(),
    val meals: UiState<List<Meal>> = UiState.Loading,
    val members: UiState<List<SpaceMember>> = UiState.Loading,
    val isSaving: Boolean = false,
    val editorSave: EditorSaveStatus = EditorSaveStatus.IDLE,
    val error: MealUiMessage? = null,
    val notice: MealUiMessage? = null,
    // Alta recién hecha desde la hoja: la pantalla la avisa con «Deshacer» y la consume.
    val created: CreatedItem? = null,
    val recipes: List<Recipe> = emptyList(),
    // Ids de la despensa del espacio, para «Con lo que hay» del selector de receta; null si no se puede leer (sin chip).
    val pantryIds: Set<String>? = null,
    // Filas a revisar al volcar ingredientes a la Compra (null = sin diálogo abierto).
    val ingredientReview: List<IngredientReviewRow>? = null,
    // Diálogo «Has cocinado …» tras marcar una comida como cocinada (null = sin diálogo).
    val cooked: CookedReview? = null,
    // El artículo de la lista que alguien ha cambiado o borrado la última vez que falló «Listo» (null si no se sabe).
    // Solo cuenta con error = CookedShoppingChanged: se lee a través de cookedErrorItem.
    val changedItemName: String? = null,
) {
    // Lunes de la semana del día enfocado (ISO: día 1 = lunes). Define la ventana observada.
    val weekStart: LocalDate get() = focusedDate.with(ChronoField.DAY_OF_WEEK, 1L)

    // Los 7 días (lunes→domingo) de la semana enfocada, para la tira selectora.
    val weekDays: List<LocalDate> get() = (0L until DAYS_IN_WEEK).map(weekStart::plusDays)

    // El artículo que nombra el error de «Has cocinado …»; null si el error es otro o no se sabe cuál.
    val cookedErrorItem: String?
        get() = changedItemName.takeIf { error == MealUiMessage.CookedShoppingChanged }

    private companion object {
        const val DAYS_IN_WEEK = 7L
    }
}
