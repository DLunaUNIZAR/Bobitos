package com.dlunaunizar.bobitos.feature.exercises

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ExerciseImageBackgroundTest {
    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun `dark theme background is not pure white`() {
        assertNotEquals(Color.White, exerciseImageBackground(dark = true))
    }

    @Test
    fun `dark theme background stays light enough for black line drawings`() {
        assertTrue(contrast(Color.Black, exerciseImageBackground(dark = true)) >= 10.0)
    }

    @Test
    fun `light theme background is white`() {
        assertEquals(Color.White, exerciseImageBackground(dark = false))
    }
}
