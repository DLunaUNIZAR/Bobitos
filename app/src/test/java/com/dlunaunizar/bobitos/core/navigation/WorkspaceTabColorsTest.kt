package com.dlunaunizar.bobitos.core.navigation

import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.contrast
import com.dlunaunizar.bobitos.core.designsystem.theme.surfaceContainerDark
import com.dlunaunizar.bobitos.core.designsystem.theme.surfaceContainerLight
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTabColorsTest {
    private fun navBarBackground(dark: Boolean) = if (dark) surfaceContainerDark else surfaceContainerLight

    @Test
    fun `selected icon reaches 3 to 1 on the indicator in every module and theme`() {
        AppModule.entries.forEach { module ->
            listOf(false, true).forEach { dark ->
                val colors = tabColors(module, dark)
                val ratio = contrast(colors.selectedIcon, colors.indicator)
                assertTrue("$module dark=$dark icon: $ratio", ratio >= 3.0)
            }
        }
    }

    @Test
    fun `selected label reaches 4_5 to 1 on the navigation bar in every module and theme`() {
        AppModule.entries.forEach { module ->
            listOf(false, true).forEach { dark ->
                val ratio = contrast(tabColors(module, dark).selectedLabel, navBarBackground(dark))
                assertTrue("$module dark=$dark label: $ratio", ratio >= 4.5)
            }
        }
    }
}
