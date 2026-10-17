package com.dlunaunizar.bobitos.feature.calendar

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class FoldedDaysRowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val start = LocalDate.of(2026, 10, 13)
    private val end = LocalDate.of(2026, 10, 16)

    private fun text(id: Int) = compose.activity.getString(id)

    private fun show(expanded: Boolean, onToggle: () -> Unit = {}) = compose.setContent {
        BobitosTheme { FoldedDaysRow(start, end, expanded, onToggle) }
    }

    @Test fun theRowIsAButtonThatAnnouncesItsState() {
        var toggles = 0
        show(expanded = false) { toggles++ }
        val node = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
        node.assert(SemanticsMatcher("is a button") { it.config[SemanticsProperties.Role] == Role.Button })
        node.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.state_collapsed)),
        )
        node.assert(
            SemanticsMatcher("describes the folded range") {
                val description = it.config[SemanticsProperties.ContentDescription].joinToString(" ")
                "13 de octubre" in description && "16 de octubre" in description && "sin eventos" in description
            },
        )
        node.performClick()
        assertEquals(1, toggles)
    }

    @Test fun theVisibleTextIsTheShortRange() {
        show(expanded = true)
        compose.onNodeWithText("mar 13 – vie 16 · Sin eventos", useUnmergedTree = true).assertTextEquals(
            "mar 13 – vie 16 · Sin eventos",
        )
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.state_expanded)),
        )
    }
}
