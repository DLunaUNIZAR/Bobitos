package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GroupedRowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun laCabeceraFijaEsEncabezadoYLeeTituloYNumero() {
        compose.setContent { BobitosTheme { SectionHeader("Arroces", count = 8, sticky = true) } }
        compose.onNodeWithText("Arroces · 8").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun unaFilaPulsableTieneClicYAlMenos48dp() {
        var clicks = 0
        compose.setContent {
            BobitosTheme {
                GroupedRow(position = RowPosition.SINGLE, onClick = { clicks++ }) { Text("Paella") }
            }
        }
        compose.onNodeWithText("Paella", useUnmergedTree = false)
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, clicks)
    }
}
