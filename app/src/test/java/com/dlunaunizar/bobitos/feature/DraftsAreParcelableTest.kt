package com.dlunaunizar.bobitos.feature

import android.os.Parcelable
import com.dlunaunizar.bobitos.feature.calendar.EventDraft
import com.dlunaunizar.bobitos.feature.exercises.CatalogExerciseDraft
import com.dlunaunizar.bobitos.feature.ingredients.BrandDraft
import com.dlunaunizar.bobitos.feature.ingredients.CatalogIngredientDraft
import com.dlunaunizar.bobitos.feature.meals.MealDraft
import com.dlunaunizar.bobitos.feature.notes.NoteDraft
import com.dlunaunizar.bobitos.feature.recipes.IngredientRow
import com.dlunaunizar.bobitos.feature.recipes.RecipeDraft
import com.dlunaunizar.bobitos.feature.routines.RoutineDraft
import com.dlunaunizar.bobitos.feature.shopping.ShoppingDraft
import com.dlunaunizar.bobitos.feature.sport.ActivityDraft
import com.dlunaunizar.bobitos.feature.tasks.TaskDraft
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftsAreParcelableTest {
    // Los editores guardan su borrador con rememberSaveable sin Saver: si un borrador deja de ser
    // Parcelable, compila igual pero la app falla al girar la pantalla con el editor abierto.
    @Test
    fun `every editor draft can be saved across a rotation`() {
        val drafts = listOf(
            NoteDraft::class,
            CatalogIngredientDraft::class,
            RoutineDraft::class,
            CatalogExerciseDraft::class,
            ShoppingDraft::class,
            ActivityDraft::class,
            MealDraft::class,
            TaskDraft::class,
            EventDraft::class,
            RecipeDraft::class,
            IngredientRow::class,
            BrandDraft::class,
        )
        drafts.forEach { draft ->
            assertTrue("${draft.simpleName} no es Parcelable", Parcelable::class.java.isAssignableFrom(draft.java))
        }
    }
}
