package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.common.BobitosDates
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DayTitleButtonTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun laFechaEsUnBotonQueAbreElCalendario() {
        val date = LocalDate.of(2026, 10, 14)
        compose.setContent { BobitosTheme { DayTitleButton(date = date, onPick = {}) } }
        val title = compose.onNodeWithText(BobitosDates.longDay(date))
        title.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        title.assertHeightIsAtLeast(48.dp)
        title.performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("octubre", substring = true, ignoreCase = true, useUnmergedTree = true)
            .fetchSemanticsNodes().let { assert(it.size > 1) { "no se ve el diálogo de fecha" } }
    }
}
