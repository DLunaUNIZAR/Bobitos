package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class WeekDayStripTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val days = (12..18).map { LocalDate.of(2026, 10, it) }
    private val selected = LocalDate.of(2026, 10, 14)
    private val today = LocalDate.of(2026, 10, 17)

    private fun show(onSelect: (LocalDate) -> Unit = {}) {
        compose.setContent {
            BobitosTheme {
                WeekDayStrip(
                    days = days,
                    selected = selected,
                    today = today,
                    onSelect = onSelect,
                    onPrevious = {},
                    onNext = {},
                    previousLabel = "Semana anterior",
                    nextLabel = "Semana siguiente",
                )
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
        show { picked = it }
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
}
