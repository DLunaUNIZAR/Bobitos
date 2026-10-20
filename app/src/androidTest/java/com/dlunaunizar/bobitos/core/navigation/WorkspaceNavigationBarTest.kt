package com.dlunaunizar.bobitos.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkspaceNavigationBarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun todasLasEtiquetasTienenElMismoTamano() {
        var labelMedium = TextUnit.Unspecified
        // A 130 % no caben a labelMedium: encogen todas juntas y ninguna se corta.
        val a130 = tamanosDeEtiquetas(1.3f) { labelMedium = it }
        a130.forEach { (title, layout) -> assertCabeEntera(title, layout) }
        val tamanos130 = a130.map { (title, layout) -> title to tamanoEfectivo(title, layout, labelMedium) }
        val (min, max) = tamanos130.minOf { it.second } to tamanos130.maxOf { it.second }
        assertTrue("Tamaños a 130 %: $tamanos130", max - min < 0.3f)
    }

    @Test
    fun aLetraNormalLasEtiquetasVanEnLabelMedium() {
        var labelMedium = TextUnit.Unspecified
        val a100 = tamanosDeEtiquetas(1f) { labelMedium = it }
        a100.forEach { (title, layout) ->
            assertCabeEntera(title, layout)
            val tamano = tamanoEfectivo(title, layout, labelMedium)
            assertEquals("$title no va en labelMedium", labelMedium.value, tamano, 0.3f)
        }
    }

    // Comprueba que la etiqueta no lleva puntos suspensivos y que tampoco se corta: sin salto de línea ni desborde.
    // hasVisualOverflow no sirve: didOverflowWidth sale true siempre que el texto es más estrecho que el máximo.
    private fun assertCabeEntera(title: String, layout: TextLayoutResult) {
        assertFalse("$title con puntos suspensivos", layout.isLineEllipsized(0))
        assertFalse("$title cortada", layout.didOverflowHeight)
        assertTrue("$title cortada", layout.getLineRight(0) <= layout.layoutInput.constraints.maxWidth)
        assertEquals("$title cortada", title.length, layout.getLineEnd(0))
    }

    // Con autoSize el estilo del layout no trae el tamaño resuelto: se deduce del ancho pintado.
    private fun tamanoEfectivo(title: String, layout: TextLayoutResult, labelMedium: TextUnit): Float {
        val input = layout.layoutInput
        val referencia = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection)
            .measure(title, input.style.copy(fontSize = labelMedium), maxLines = 1, softWrap = false)
        return labelMedium.value * layout.size.width / referencia.size.width
    }

    private fun tamanosDeEtiquetas(
        fontScale: Float,
        onLabelMedium: (TextUnit) -> Unit,
    ): List<Pair<String, TextLayoutResult>> {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                BobitosTheme {
                    onLabelMedium(MaterialTheme.typography.labelMedium.fontSize)
                    Box(Modifier.width(411.dp)) {
                        WorkspaceNavigationBar(selectedTab = BobitosDestination.SpaceHome, onTabSelected = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        return BobitosDestination.workspaceTabs.map { tab ->
            val title = compose.activity.getString(tab.titleRes)
            val results = mutableListOf<TextLayoutResult>()
            val node = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode()
            val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
            assertNotNull("$title sin GetTextLayoutResult", action)
            action!!(results)
            title to results.first()
        }
    }
}
