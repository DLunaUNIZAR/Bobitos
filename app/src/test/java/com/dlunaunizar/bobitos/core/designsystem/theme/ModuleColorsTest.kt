package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleColorsTest {
    private fun channel(v: Float): Float =
        if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

    private fun luminance(c: Color): Float =
        0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)

    private fun contrast(a: Color, b: Color): Float {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    @Test
    fun todosLosParesCumplenContraste() {
        val backgrounds = mapOf(false to Color(0xFFFBFAF8), true to Color(0xFF141513))
        for (dark in listOf(false, true)) {
            for (module in AppModule.entries) {
                val c = ModulePalette.colors(module, dark)
                assertTrue(
                    "$module dark=$dark texto sobre contenedor",
                    contrast(c.onContainer, c.container) >= 4.5f,
                )
                assertTrue(
                    "$module dark=$dark acento sobre fondo",
                    contrast(c.accent, backgrounds.getValue(dark)) >= 3f,
                )
            }
        }
    }
}
