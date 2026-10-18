package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleColorsTest {
    private val temas = listOf(false, true)

    private fun fondo(dark: Boolean) = if (dark) backgroundDark else backgroundLight
    private fun onSurface(dark: Boolean) = if (dark) onSurfaceDark else onSurfaceLight
    private fun onSurfaceVariant(dark: Boolean) = if (dark) onSurfaceVariantDark else onSurfaceVariantLight

    private fun paraCadaModuloYTema(comprobar: (AppModule, Boolean, ModuleColors, Color) -> Unit) {
        for (dark in temas) {
            for (module in AppModule.entries) {
                comprobar(module, dark, ModulePalette.colors(module, dark), ModulePalette.tint(module, dark))
            }
        }
    }

    @Test
    fun losSieteModulosTienenColor() {
        assertTrue(AppModule.entries.contains(AppModule.NOTES))
        assertTrue(AppModule.entries.contains(AppModule.PANTRY))
        assertTrue(AppModule.entries.size == 7)
        paraCadaModuloYTema { module, dark, c, _ ->
            assertTrue("$module dark=$dark", c.accent != Color.Unspecified && c.onAccent != Color.Unspecified)
        }
    }

    @Test
    fun onContainerSeLeeSobreContainer() = paraCadaModuloYTema { module, dark, c, _ ->
        assertTrue("$module dark=$dark", contrast(c.onContainer, c.container) >= 4.5)
    }

    @Test
    fun elAcentoSeLeeComoTextoSobreElFondoYSobreSuTinte() = paraCadaModuloYTema { module, dark, c, tint ->
        assertTrue("$module dark=$dark fondo", contrast(c.accent, fondo(dark)) >= 4.5)
        assertTrue("$module dark=$dark tinte", contrast(c.accent, tint) >= 4.5)
    }

    @Test
    fun loQueVaSobreElAcentoSeLee() = paraCadaModuloYTema { module, dark, c, _ ->
        assertTrue("$module dark=$dark", contrast(c.onAccent, c.accent) >= 4.5)
    }

    @Test
    fun elTextoSeLeeSobreElTinte() = paraCadaModuloYTema { module, dark, _, tint ->
        assertTrue("$module dark=$dark onSurface", contrast(onSurface(dark), tint) >= 4.5)
        assertTrue("$module dark=$dark onSurfaceVariant", contrast(onSurfaceVariant(dark), tint) >= 4.5)
    }

    @Test
    fun elAcentoComoContornoSeVe() = paraCadaModuloYTema { module, dark, c, tint ->
        assertTrue("$module dark=$dark fondo", contrast(c.accent, fondo(dark)) >= 3.0)
        assertTrue("$module dark=$dark tinte", contrast(c.accent, tint) >= 3.0)
    }

    @Test
    fun enClaroElTinteEsSuave() {
        for (module in AppModule.entries) {
            val ratio = contrast(ModulePalette.tint(module, dark = false), backgroundLight)
            assertTrue("$module: $ratio", ratio <= 1.15)
        }
    }

    @Test
    fun enOscuroElTinteNoCambia() {
        for (module in AppModule.entries) {
            val esperado = ModulePalette.colors(module, true).accent.copy(alpha = 0.16f).compositeOver(backgroundDark)
            assertEquals("$module", esperado, ModulePalette.tint(module, dark = true))
        }
    }
}
