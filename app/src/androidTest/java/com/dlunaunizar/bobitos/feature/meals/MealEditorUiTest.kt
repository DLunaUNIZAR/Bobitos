package com.dlunaunizar.bobitos.feature.meals

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class MealEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tortilla = Recipe(
        id = "r1",
        ownerUid = "u1",
        visibility = RecipeVisibility.PRIVATE,
        title = "Tortilla de patatas",
        description = null,
        category = null,
        createdBy = "u1",
        createdByName = "Ana",
        createdAt = Instant.EPOCH,
        updatedBy = "u1",
        updatedAt = Instant.EPOCH,
    )

    private fun text(id: Int) = compose.activity.getString(id)

    @Test fun theRecipePickerStaysOpenWithItsSearchAfterRotating() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            BobitosTheme {
                MealEditor(
                    meal = null,
                    initialSlot = MealSlot.COMIDA,
                    members = emptyList(),
                    recipes = listOf(tortilla),
                    pantryIds = null,
                    saving = false,
                    errorMessage = null,
                    canWrite = true,
                    onDismiss = {},
                    onSave = { _, _, _, _, _ -> },
                )
            }
        }
        compose.onNodeWithText(text(R.string.meals_choose_recipe)).performClick()
        compose.onNodeWithText(text(R.string.meals_recipe_picker_title)).assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipes_search_label))).performTextInput("Tort")

        // Lo que pasa al girar el móvil: se guarda el estado, se descarta la composición y se recrea.
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText(text(R.string.meals_recipe_picker_title)).assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Tort")).assertIsDisplayed()
    }
}
