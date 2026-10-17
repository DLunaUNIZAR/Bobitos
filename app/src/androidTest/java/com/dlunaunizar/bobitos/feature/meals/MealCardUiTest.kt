package com.dlunaunizar.bobitos.feature.meals

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MealCardUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val meal = Meal(
        id = "m1",
        date = LocalDate.of(2026, 10, 17),
        slot = MealSlot.COMIDA,
        name = "Cocido de la abuela",
        participantIds = listOf("u1", "u2"),
        participantNames = listOf("Ana", "David"),
        recipeId = "r1",
        createdBy = "u1",
        createdByName = "Ana",
        createdAt = Instant.EPOCH,
        updatedBy = "u1",
        updatedAt = Instant.EPOCH,
    )

    private val recipe = Recipe(
        id = "r1",
        ownerUid = "u1",
        visibility = RecipeVisibility.PRIVATE,
        title = "Cocido",
        description = null,
        category = null,
        ingredients = listOf(
            Ingredient("garbanzos", "400", "g"),
            Ingredient("zanahoria"),
            Ingredient("patata"),
            Ingredient("puerro"),
            Ingredient("chorizo"),
        ),
        createdBy = "u1",
        createdByName = "Ana",
        createdAt = Instant.EPOCH,
        updatedBy = "u1",
        updatedAt = Instant.EPOCH,
    )

    private fun show(onClick: (() -> Unit)?) = compose.setContent {
        BobitosTheme {
            MealCard(
                meal = meal,
                recipe = recipe,
                enabled = true,
                canWrite = onClick != null,
                onClick = onClick,
                onEdit = {},
                onDelete = {},
                onAddToShopping = {},
                onToggleCooked = {},
            )
        }
    }

    @Test
    fun tappingTheCardOpensItsEditor() {
        var clicks = 0
        show { clicks++ }

        compose.onNodeWithText("Cocido de la abuela").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun withoutAClickTheCardIsNotClickable() {
        show(null)

        compose.onNodeWithText("Cocido de la abuela").assertIsDisplayed()
        compose.onNodeWithText("Cocido de la abuela")
            .assert(SemanticsMatcher("no OnClick") { SemanticsActions.OnClick !in it.config })
    }

    @Test
    fun theCardSummarisesInsteadOfListingIngredients() {
        show {}

        compose.onNodeWithText("5 ingredientes", substring = true).assertIsDisplayed()
        compose.onNodeWithText("400 g garbanzos", substring = true).assertDoesNotExist()
    }
}
