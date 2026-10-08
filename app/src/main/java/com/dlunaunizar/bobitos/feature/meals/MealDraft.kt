package com.dlunaunizar.bobitos.feature.meals

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import kotlinx.parcelize.Parcelize

// Borrador del editor de comida. Parcelable para que sobreviva a una rotación.
@Parcelize
internal data class MealDraft(
    val name: String,
    val recipeId: String?,
    val selectedIds: List<String>,
    val cookId: String?,
    val slot: MealSlot,
) : Parcelable {
    // Escribir a mano desvincula la receta elegida.
    fun withName(value: String) = copy(name = value, recipeId = null)

    fun withRecipe(title: String, id: String) = copy(name = title, recipeId = id)

    // Si el cocinero deja de ser participante, se descarta.
    fun withParticipant(userId: String, selected: Boolean) = copy(
        selectedIds = if (selected) (selectedIds + userId).distinct() else selectedIds - userId,
        cookId = if (!selected && userId == cookId) null else cookId,
    )

    companion object {
        fun of(meal: Meal?, slot: MealSlot) = MealDraft(
            name = meal?.name.orEmpty(),
            recipeId = meal?.recipeId,
            selectedIds = meal?.participantIds.orEmpty().distinct().sorted(),
            cookId = meal?.cookId,
            slot = meal?.slot ?: slot,
        )
    }
}

/** Franja que se propone al añadir una comida sin elegir antes la franja (FAB), según la hora. */
internal fun defaultMealSlot(hour: Int): MealSlot = when (hour) {
    in 5..11 -> MealSlot.DESAYUNO
    in 12..16 -> MealSlot.COMIDA
    else -> MealSlot.CENA
}
