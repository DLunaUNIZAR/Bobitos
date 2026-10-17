package com.dlunaunizar.bobitos.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class WorkspaceNavigationBarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun labelsAreNotEllipsizedAt130PercentFontScale() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                BobitosTheme {
                    Box(Modifier.width(411.dp)) {
                        WorkspaceNavigationBar(selectedTab = BobitosDestination.SpaceHome, onTabSelected = {})
                    }
                }
            }
        }
        BobitosDestination.workspaceTabs.forEach { tab ->
            val title = compose.activity.getString(tab.titleRes)
            val results = mutableListOf<TextLayoutResult>()
            val node = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode()
            val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
            assertNotNull("$title sin GetTextLayoutResult", action)
            action!!(results)
            assertFalse("$title cortada", results.first().isLineEllipsized(0))
        }
    }
}
