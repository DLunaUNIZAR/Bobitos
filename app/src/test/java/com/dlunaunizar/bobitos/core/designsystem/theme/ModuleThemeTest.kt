package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleThemeTest {
    @Test fun mapeaLosRolesDeAcentoAlModulo() {
        val base = lightColorScheme(
            background = Color(0xFF111111),
            surface = Color(0xFF222222),
            error = Color(0xFF333333),
        )
        val colors = ModulePalette.colors(AppModule.MEALS, dark = false)

        val scheme = moduleColorScheme(base, colors)

        assertEquals(colors.accent, scheme.primary)
        assertEquals(colors.onAccent, scheme.onPrimary)
        assertEquals(colors.container, scheme.primaryContainer)
        assertEquals(colors.onContainer, scheme.onPrimaryContainer)
        assertEquals(colors.container, scheme.secondaryContainer)
        assertEquals(colors.onContainer, scheme.onSecondaryContainer)
        assertEquals(colors.accent, scheme.surfaceTint)
        assertEquals(base.background, scheme.background)
        assertEquals(base.surface, scheme.surface)
        assertEquals(base.error, scheme.error)
    }
}
