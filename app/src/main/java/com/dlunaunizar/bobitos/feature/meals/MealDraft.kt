package com.dlunaunizar.bobitos.feature.meals

import androidx.compose.runtime.saveable.listSaver
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot

// Borrador del editor de comida: solo tipos que caben en un Bundle, para que sobreviva a una rotación.
// `slotName` es el `name` de la franja.
internal data class MealDraft(
    val name: String,
    val recipeId: String?,
    val selectedIds: List<String>,
    val cookId: String?,
    val slotName: String,
) {
    val slot: MealSlot get() = MealSlot.valueOf(slotName)

    // Escribir a mano desvincula la receta elegida.
    fun withName(value: String) = copy(name = value, recipeId = null)

    fun withRecipe(title: String, id: String) = copy(name = title, recipeId = id)

    // Si el cocinero deja de ser participante, se descarta.
    fun withParticipant(userId: String, selected: Boolean) = copy(
        selectedIds = if (selected) (selectedIds + userId).distinct() else selectedIds - userId,
        cookId = if (!selected && userId == cookId) null else cookId,
    )

    fun withSlot(value: MealSlot) = copy(slotName = value.name)

    companion object {
        fun of(meal: Meal?, slot: MealSlot) = MealDraft(
            name = meal?.name.orEmpty(),
            recipeId = meal?.recipeId,
            selectedIds = meal?.participantIds.orEmpty().distinct().sorted(),
            cookId = meal?.cookId,
            slotName = (meal?.slot ?: slot).name,
        )
    }
}

internal val MealDraftSaver = listSaver<MealDraft, Any?>(
    save = { listOf(it.name, it.recipeId, ArrayList(it.selectedIds), it.cookId, it.slotName) },
    restore = {
        @Suppress("UNCHECKED_CAST")
        MealDraft(
            name = it[0] as String,
            recipeId = it[1] as String?,
            selectedIds = (it[2] as List<String>).toList(),
            cookId = it[3] as String?,
            slotName = it[4] as String,
        )
    },
)

/** Franja que se propone al añadir una comida sin elegir antes la franja (FAB), según la hora. */
internal fun defaultMealSlot(hour: Int): MealSlot = when (hour) {
    in 5..11 -> MealSlot.DESAYUNO
    in 12..16 -> MealSlot.COMIDA
    else -> MealSlot.CENA
}
