package com.dlunaunizar.bobitos.feature.meals

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MealSlotSectionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(canWrite: Boolean, onAdd: (MealSlot) -> Unit = {}) {
        compose.setContent {
            BobitosTheme {
                MealSlotSection(
                    slot = MealSlot.CENA,
                    meals = emptyList(),
                    recipes = emptyList(),
                    canWrite = canWrite,
                    actionsEnabled = canWrite,
                    onAdd = onAdd,
                    onEdit = {},
                    onDelete = {},
                    onAddToShopping = {},
                    onToggleCooked = {},
                )
            }
        }
    }

    @Test
    fun laFranjaVaciaPermiteAnadirEnElla() {
        val added = mutableListOf<MealSlot>()
        show(canWrite = true) { added += it }
        compose.onNodeWithText("Añadir cena").assertIsDisplayed().performClick()
        assertEquals(listOf(MealSlot.CENA), added)
    }

    @Test
    fun sinConexionLaFranjaVaciaSoloDiceSinPlanificar() {
        show(canWrite = false)
        compose.onNodeWithText("Sin planificar").assertIsDisplayed()
        compose.onAllNodesWithText("Añadir cena").assertCountEquals(0)
    }

    @Test
    fun laCabeceraDeFranjaEsEncabezado() {
        show(canWrite = true)
        compose.onNodeWithText("Cena")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }
}
