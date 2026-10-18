package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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

class FilterDropdownTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var elegida by mutableStateOf("Pendientes")
    private val avisos = mutableListOf<String>()

    private fun opciones() = listOf("Pendientes", "Completadas").map { texto ->
        FilterOption(texto, elegida == texto) {
            avisos += texto
            elegida = texto
        }
    }

    @Test
    fun muestraNombreYValor() {
        compose.setContent {
            BobitosTheme { FilterDropdown("Estado", elegida, opciones(), tag = "estado") }
        }
        compose.onNodeWithTag("estado")
            .assertTextContains("Estado", substring = true)
            .assertTextContains("Pendientes", substring = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
    }

    @Test
    fun elMenuMarcaLaOpcionElegida() {
        compose.setContent {
            BobitosTheme { FilterDropdown("Estado", elegida, opciones(), tag = "estado") }
        }
        compose.onNodeWithTag("estado").performClick()
        compose.onNode(hasText("Pendientes") and estaSeleccionado(true)).assertExists()
        compose.onNode(hasText("Completadas") and estaSeleccionado(false)).assertExists()
    }

    @Test
    fun elegirUnaOpcionAvisaYCierra() {
        compose.setContent {
            BobitosTheme { FilterDropdown("Estado", elegida, opciones(), tag = "estado") }
        }
        compose.onNodeWithTag("estado").performClick()
        compose.onNodeWithText("Completadas").performClick()
        compose.waitForIdle()
        assertEquals(listOf("Completadas"), avisos)
        // Con el menú cerrado «Completadas» solo queda como valor del campo; abierto saldría dos veces.
        compose.onAllNodesWithText("Completadas").assertCountEquals(1)
    }

    @Test
    fun laSeleccionMultipleNoCierraElMenu() {
        val marcadas = mutableStateOf(setOf<String>())
        compose.setContent {
            BobitosTheme {
                MultiSelectFilterDropdown(
                    label = "Personas",
                    value = "Todas",
                    options = listOf("Ana", "Luis").map { nombre ->
                        FilterOption(nombre, nombre in marcadas.value) {
                            marcadas.value = marcadas.value.toggle(nombre)
                        }
                    },
                    tag = "personas",
                )
            }
        }
        compose.onNodeWithTag("personas").performClick()
        compose.onNodeWithText("Ana").performClick()
        compose.onNodeWithText("Luis").performClick()
        compose.waitForIdle()
        assertEquals(setOf("Ana", "Luis"), marcadas.value)
        compose.onNode(hasText("Ana") and estaSeleccionado(true)).assertExists()
        compose.onNode(hasText("Luis") and estaSeleccionado(true)).assertExists()
    }

    @Test
    fun conLetraGrandeNoSeCorta() {
        compose.setContent {
            BobitosTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f),
                ) {
                    Box(Modifier.width(360.dp)) {
                        FilterDropdownRow {
                            FilterDropdown("Categoría", "Desayunos y meriendas", opciones(), tag = "a")
                            FilterDropdown("Supermercado", "Todos", opciones(), tag = "b")
                        }
                    }
                }
            }
        }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult))
            .fetchSemanticsNodes().forEach { nodo ->
                val resultados = mutableListOf<TextLayoutResult>()
                nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
                resultados.forEach { assertFalse("texto cortado", it.hasVisualOverflow) }
            }
    }

    @Test
    fun cuatroDesplegablesSalenEnDosFilas() {
        compose.setContent {
            BobitosTheme {
                Box(Modifier.width(900.dp)) {
                    FilterDropdownRow {
                        listOf("a", "b", "c", "d").forEach {
                            FilterDropdown(it, "Todos", opciones(), Modifier.weight(1f), tag = it)
                        }
                    }
                }
            }
        }
        val tops = listOf("a", "b", "c", "d").map {
            compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(tops[0], tops[1], 1f)
        assertEquals(tops[2], tops[3], 1f)
        assertTrue(tops[2] > tops[0])
    }

    private fun Set<String>.toggle(x: String) = if (x in this) this - x else this + x

    private fun estaSeleccionado(valor: Boolean) = SemanticsMatcher.expectValue(SemanticsProperties.Selected, valor)
}
