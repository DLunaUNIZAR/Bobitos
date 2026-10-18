package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
                    primaryAction = InfoSheetAction("Añadir a la compra", onClick = { calls++ }),
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
                    secondaryActions = listOf(
                        InfoSheetAction("Editar", onClick = {}),
                        InfoSheetAction("Eliminar", onClick = {}),
                    ),
                ) { Text("Contenido") }
            }
        }
        compose.onNodeWithContentDescription("Más opciones de «Ensalada»").performClick()
        compose.onNodeWithText("Editar").assertIsDisplayed()
        compose.onNodeWithText("Eliminar").assertIsDisplayed()
    }

    @Test
    fun aDisabledSecondaryActionShowsDisabledInTheMenu() {
        compose.setContent {
            BobitosTheme {
                BobitosInfoSheet(
                    title = "Ensalada",
                    onDismiss = {},
                    secondaryActions = listOf(
                        InfoSheetAction("Editar", onClick = {}),
                        InfoSheetAction("Eliminar", enabled = false, onClick = {}),
                    ),
                ) { Text("Contenido") }
            }
        }
        compose.onNodeWithContentDescription("Más opciones de «Ensalada»").performClick()
        compose.onNodeWithText("Editar").assertIsEnabled()
        compose.onNodeWithText("Eliminar").assertIsNotEnabled()
    }

    @Test
    fun aDisabledPrimaryActionShowsDisabled() {
        compose.setContent {
            BobitosTheme {
                BobitosInfoSheet(
                    title = "Ensalada",
                    onDismiss = {},
                    primaryAction = InfoSheetAction("Editar", enabled = false, onClick = {}),
                ) { Text("Contenido") }
            }
        }
        compose.onNodeWithText("Editar").assertIsNotEnabled()
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

    // Los botones se miden aparte de la ventana del sheet: la densidad no llega a un diálogo, el ancho sí se puede fijar.
    private fun botonesConLetraGrande(anchoDp: Int, escala: Float) {
        compose.setContent {
            val densidad = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density = densidad, fontScale = escala)) {
                BobitosTheme {
                    Box(Modifier.width(anchoDp.dp)) {
                        SheetButtons(
                            close = { TextButton(onClick = {}) { Text("Cerrar") } },
                            primary = { Button(onClick = {}) { Text("Añadir a la compra") } },
                        )
                    }
                }
            }
        }
    }

    private fun assertPrincipalNoSeCorta() {
        val resultados = mutableListOf<TextLayoutResult>()
        val nodo = compose.onNodeWithText("Añadir a la compra").fetchSemanticsNode()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        val texto = resultados.single()
        assertEquals(1, texto.lineCount)
        assertFalse(texto.isLineEllipsized(0))
        assertTrue("el texto debe caber entero en su nodo", nodo.size.width >= texto.multiParagraph.maxIntrinsicWidth)
    }

    @Test
    fun conLetraGrandeLosBotonesNoSeCortan() {
        botonesConLetraGrande(anchoDp = 312, escala = 1.3f)
        assertPrincipalNoSeCorta()
    }

    @Test
    fun conLetraGrandeLosBotonesSeApilanSinCortarse() {
        // En 360 dp de pantalla (312 dp útiles) y con letra al 200 % no caben en una fila.
        botonesConLetraGrande(anchoDp = 312, escala = 2f)
        assertPrincipalNoSeCorta()
        val abajoPrincipal = compose.onNodeWithText("Añadir a la compra").getBoundsInRoot().bottom
        val arribaCerrar = compose.onNodeWithText("Cerrar").getBoundsInRoot().top
        assertTrue("el principal debe quedar encima de «Cerrar»", abajoPrincipal <= arribaCerrar)
    }
}
