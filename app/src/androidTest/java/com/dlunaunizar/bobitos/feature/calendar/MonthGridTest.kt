package com.dlunaunizar.bobitos.feature.calendar

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthGridTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val esCelda = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    // Un móvil común con la barra grande abierta deja poco alto al mes: 360 x 380 dp a escala 1,3.
    private fun mostrar() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                BobitosTheme {
                    Box(Modifier.width(360.dp).height(380.dp)) {
                        MonthGrid(
                            month = YearMonth.of(2026, 10),
                            selected = LocalDate.of(2026, 10, 18),
                            events = emptyList(),
                            select = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun ningunaFilaDelMesQuedaAplastadaYSePuedeLlegarALaUltima() {
        mostrar()

        val celdas = compose.onAllNodes(esCelda)
        val alturas = celdas.fetchSemanticsNodes().map { it.size.height }
        assertEquals(42, alturas.size)
        assertTrue("alturas distintas: ${alturas.toSet()}", alturas.toSet().size == 1)

        celdas[41].performScrollTo().assertIsDisplayed()
    }
}
