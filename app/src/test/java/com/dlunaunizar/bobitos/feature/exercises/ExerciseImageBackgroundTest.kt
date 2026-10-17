package com.dlunaunizar.bobitos.feature.exercises

import androidx.compose.ui.graphics.Color
import com.dlunaunizar.bobitos.core.designsystem.theme.contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseImageBackgroundTest {
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
