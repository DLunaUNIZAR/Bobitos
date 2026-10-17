package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MealSummaryTest {
    private fun meal(recipeId: String? = "r1", names: List<String> = listOf("Ana", "David")) = Meal(
        id = "m1",
        date = LocalDate.of(2026, 10, 17),
        slot = MealSlot.COMIDA,
        name = "Cocido",
        participantIds = names.map { "u-$it" },
        participantNames = names,
        recipeId = recipeId,
        createdBy = "u1",
        createdByName = "Ana",
        createdAt = Instant.EPOCH,
        updatedBy = "u1",
        updatedAt = Instant.EPOCH,
    )

    private fun recipe(count: Int?) = Recipe(
        id = "r1",
        ownerUid = "u1",
        visibility = RecipeVisibility.PRIVATE,
        title = "Cocido",
        description = null,
        category = null,
        ingredients = count?.let { n -> List(n) { Ingredient(name = "ing$it") } },
        createdBy = "u1",
        createdByName = "Ana",
        createdAt = Instant.EPOCH,
        updatedBy = "u1",
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun countsIngredientsAndKeepsParticipants() {
        assertEquals(MealSummary(5, listOf("Ana", "David")), mealSummary(meal(), recipe(5)))
    }

    @Test
    fun noRecipeMeansNoCount() = assertNull(mealSummary(meal(recipeId = null), null).ingredientCount)

    @Test
    fun emptyRecipeMeansNoCount() = assertNull(mealSummary(meal(), recipe(0)).ingredientCount)

    @Test
    fun noParticipantsGivesEmptyList() =
        assertEquals(emptyList<String>(), mealSummary(meal(names = emptyList()), recipe(2)).participantNames)
}
