package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SectionHeaderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun stateIs(text: String) = SemanticsMatcher("StateDescription is $text") {
        it.config.getOrNull(SemanticsProperties.StateDescription) == text
    }

    @Test
    fun aPlainHeaderIsAHeadingWithItsCount() {
        compose.setContent { BobitosTheme { SectionHeader("Hoy", count = 2) } }
        compose.onNodeWithText("Hoy · 2").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun aCollapsibleHeaderAnnouncesItsState() {
        var calls = 0
        var expanded = false
        compose.setContent {
            BobitosTheme { SectionHeader("Completadas", expanded = expanded, onToggle = { calls++ }) }
        }
        val toggle = compose.onNode(isButton)
        toggle.assertHasClickAction().assert(stateIs("Plegado"))
        toggle.performClick()
        assertEquals(1, calls)
    }

    @Test
    fun anExpandedHeaderSaysDesplegado() {
        compose.setContent { BobitosTheme { SectionHeader("Completadas", expanded = true, onToggle = {}) } }
        compose.onNode(isButton).assert(stateIs("Desplegado"))
    }

    @Test
    fun theActionIsNotPartOfTheToggle() {
        var toggles = 0
        var actions = 0
        compose.setContent {
            BobitosTheme {
                SectionHeader(
                    "Pendientes",
                    expanded = true,
                    onToggle = { toggles++ },
                    action = { TextButton(onClick = { actions++ }) { Text("Plantillas") } },
                )
            }
        }
        compose.onNode(hasText("Plantillas")).performClick()
        assertEquals(0, toggles)
        assertEquals(1, actions)
    }
}
