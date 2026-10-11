package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BobitosThemeContentColorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun contentColorAndOnBackground(dark: Boolean): Pair<Color, Color> {
        var content = Color.Unspecified
        var onBackground = Color.Unspecified
        compose.setContent {
            BobitosTheme(darkTheme = dark) {
                content = LocalContentColor.current
                onBackground = MaterialTheme.colorScheme.onBackground
            }
        }
        compose.waitForIdle()
        return content to onBackground
    }

    @Test fun darkThemeProvidesOnBackgroundAsContentColorWithoutSurface() {
        val (content, onBackground) = contentColorAndOnBackground(dark = true)
        assertEquals(onBackground, content)
    }

    @Test fun lightThemeProvidesOnBackgroundAsContentColorWithoutSurface() {
        val (content, onBackground) = contentColorAndOnBackground(dark = false)
        assertEquals(onBackground, content)
    }
}
