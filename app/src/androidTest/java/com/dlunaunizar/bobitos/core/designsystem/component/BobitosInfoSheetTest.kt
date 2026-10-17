package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BobitosInfoSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun thePrimaryActionIsAFilledButtonNextToClose() {
        var calls = 0
        compose.setContent {
            BobitosTheme {
                BobitosInfoSheet(
                    title = "Ensalada",
                    onDismiss = {},
                    primaryAction = InfoSheetAction("Añadir a la compra") { calls++ },
                ) { Text("Contenido") }
            }
        }
        compose.onNodeWithText("Cerrar").assertIsDisplayed()
        compose.onNodeWithText("Añadir a la compra").assertIsDisplayed().performClick()
        assertEquals(1, calls)
    }

    @Test
    fun secondaryActionsLiveInANamedMenu() {
        compose.setContent {
            BobitosTheme {
                BobitosInfoSheet(
                    title = "Ensalada",
                    onDismiss = {},
                    secondaryActions = listOf(InfoSheetAction("Editar") {}, InfoSheetAction("Eliminar") {}),
                ) { Text("Contenido") }
            }
        }
        compose.onNodeWithContentDescription("Más opciones de «Ensalada»").performClick()
        compose.onNodeWithText("Editar").assertIsDisplayed()
        compose.onNodeWithText("Eliminar").assertIsDisplayed()
    }

    @Test
    fun withoutSecondariesThereIsNoMenu() {
        compose.setContent {
            BobitosTheme {
                BobitosInfoSheet(title = "Ensalada", onDismiss = {}) { Text("Contenido") }
            }
        }
        compose.onAllNodes(
            SemanticsMatcher("description starts with Más opciones") {
                it.config.getOrNull(SemanticsProperties.ContentDescription)
                    ?.any { d -> d.startsWith("Más opciones") } == true
            },
        ).assertCountEquals(0)
    }
}
