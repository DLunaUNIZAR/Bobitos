package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.designsystem.theme.ModulePalette
import com.dlunaunizar.bobitos.core.designsystem.theme.ModuleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class WeekDayStripTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val days = (12..18).map { LocalDate.of(2026, 10, it) }
    private val selected = LocalDate.of(2026, 10, 14)
    private val today = LocalDate.of(2026, 10, 17)

    private fun show(onSelect: (LocalDate) -> Unit = {}, onPrevious: () -> Unit = {}, onNext: () -> Unit = {}) {
        compose.setContent {
            BobitosTheme {
                Box(Modifier.width(360.dp).testTag("tira")) {
                    WeekDayStrip(
                        days = days,
                        selected = selected,
                        today = today,
                        onSelect = onSelect,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        previousLabel = "Semana anterior",
                        nextLabel = "Semana siguiente",
                    )
                }
            }
        }
    }

    @Test
    fun eachDayIsSelectableWithItsFullName() {
        show()
        compose.onNodeWithContentDescription("miércoles, 14 de octubre", substring = true).assertIsSelected()
        compose.onNodeWithContentDescription("martes, 13 de octubre", substring = true).assertIsNotSelected()
    }

    @Test
    fun todayIsAnnounced() {
        show()
        compose.onNodeWithContentDescription("sábado, 17 de octubre", substring = true)
            .assert(
                SemanticsMatcher("says hoy") {
                    it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.contains("hoy") } == true
                },
            )
    }

    @Test
    fun initialAndNumberAreNotReadSeparately() {
        show()
        compose.onAllNodesWithText("14").assertCountEquals(0)
        compose.onAllNodesWithText("X").assertCountEquals(0)
    }

    @Test
    fun tappingADaySelectsIt() {
        var picked: LocalDate? = null
        show(onSelect = { picked = it })
        compose.onNodeWithContentDescription("martes, 13 de octubre", substring = true).performClick()
        assertEquals(LocalDate.of(2026, 10, 13), picked)
    }

    @Test
    fun daysAreTallEnough() {
        show()
        days.forEach {
            compose.onNode(hasContentDescription("${it.dayOfMonth} de octubre", substring = true))
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun theStripIsASelectableGroup() {
        show()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).assertExists()
    }

    @Test
    fun cadaDiaEsUnaOpcionUnica() {
        show()
        days.forEach {
            compose.onNode(hasContentDescription("${it.dayOfMonth} de octubre", substring = true))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
    }

    @Test
    fun cadaDiaMideAlMenos48dpDeAncho() {
        show()
        days.forEach {
            compose.onNode(hasContentDescription("${it.dayOfMonth} de octubre", substring = true))
                .assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun noHayFlechas() {
        show()
        compose.onAllNodes(hasClickAction() and hasContentDescription("Semana anterior")).assertCountEquals(0)
        compose.onAllNodes(hasClickAction() and hasContentDescription("Semana siguiente")).assertCountEquals(0)
    }

    @Test
    fun deslizarALaIzquierdaPasaALaSemanaSiguiente() {
        var next = 0
        var previous = 0
        show(onPrevious = { previous++ }, onNext = { next++ })
        compose.onNodeWithTag("tira").performTouchInput { swipeLeft() }
        assertEquals(1, next)
        assertEquals(0, previous)
    }

    @Test
    fun deslizarALaDerechaVuelveALaAnterior() {
        var next = 0
        var previous = 0
        show(onPrevious = { previous++ }, onNext = { next++ })
        compose.onNodeWithTag("tira").performTouchInput { swipeRight() }
        assertEquals(1, previous)
        assertEquals(0, next)
    }

    @Test
    fun unArrastreCortoNoCambiaDeSemana() {
        var calls = 0
        show(onPrevious = { calls++ }, onNext = { calls++ })
        compose.onNodeWithTag("tira").performTouchInput {
            swipe(start = center, end = center - Offset(24.dp.toPx(), 0f), durationMillis = 200)
        }
        assertEquals(0, calls)
    }

    @Test
    fun unArrastreMasVerticalQueHorizontalNoCambiaDeSemana() {
        var calls = 0
        show(onPrevious = { calls++ }, onNext = { calls++ })
        compose.onNodeWithTag("tira").performTouchInput {
            swipe(start = center, end = center - Offset(80.dp.toPx(), 160.dp.toPx()), durationMillis = 200)
        }
        assertEquals(0, calls)
    }

    @Test
    fun cadaDiaTieneLasAccionesDeSemana() {
        var next = 0
        var previous = 0
        show(onPrevious = { previous++ }, onNext = { next++ })
        // El nodo que enfoca TalkBack es el del día (seleccionable), no el contenedor.
        val dias = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
        dias.assertCountEquals(7)
        repeat(7) { i ->
            val actions = dias[i].fetchSemanticsNode().config[SemanticsActions.CustomActions]
            assertEquals(listOf("Semana anterior", "Semana siguiente"), actions.map { it.label })
        }
        val actions = dias[0].fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnUiThread { actions.forEach { it.action() } }
        assertEquals(1, previous)
        assertEquals(1, next)
        // El contenedor no las repite.
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).assertCountEquals(7)
    }

    @Test
    fun unGestoRapidoDentroDeUnDiaSoloCambiaDeSemana() {
        var next = 0
        var previous = 0
        var picked: LocalDate? = null
        show(onSelect = { picked = it }, onPrevious = { previous++ }, onNext = { next++ })
        // 30 dp en 30 ms (1000 dp/s) que empiezan y acaban dentro de la celda de un día (~50 dp).
        compose.onNodeWithContentDescription("martes, 13 de octubre", substring = true).performTouchInput {
            val y = center.y
            swipe(start = Offset(40.dp.toPx(), y), end = Offset(10.dp.toPx(), y), durationMillis = 30)
        }
        assertEquals(1, next)
        assertEquals(0, previous)
        assertEquals(null, picked)
    }

    @Test
    fun losColoresDelDiaElegidoSonLosDelTestDeContraste() {
        val modules = listOf(AppModule.MEALS, AppModule.TASKS, AppModule.PANTRY)
        listOf(false, true).forEach { dark ->
            modules.forEach { module ->
                compose.activityRule.scenario.recreate()
                var seen: WeekDayStripColors? = null
                compose.setContent {
                    BobitosTheme(darkTheme = dark) {
                        ModuleTheme(module) { seen = WeekDayStripDefaults.colors() }
                    }
                }
                compose.waitForIdle()
                val (container, content) = ModulePalette.selectedDay(module, dark)
                assertEquals(container, seen?.selectedContainer)
                assertEquals(content, seen?.selectedContent)
            }
        }
    }
}
