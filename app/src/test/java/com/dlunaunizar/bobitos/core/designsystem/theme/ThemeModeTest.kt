package com.dlunaunizar.bobitos.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun `a missing preference defaults to dark`() {
        assertEquals(ThemeMode.DARK, parseThemeMode(null))
    }

    @Test
    fun `an unknown value defaults to dark`() {
        assertEquals(ThemeMode.DARK, parseThemeMode("SEPIA"))
    }

    @Test
    fun `stored choices are kept`() {
        assertEquals(ThemeMode.LIGHT, parseThemeMode("LIGHT"))
        assertEquals(ThemeMode.DARK, parseThemeMode("DARK"))
        assertEquals(ThemeMode.SYSTEM, parseThemeMode("SYSTEM"))
    }
}
