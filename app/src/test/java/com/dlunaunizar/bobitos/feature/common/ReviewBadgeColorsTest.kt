package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.core.designsystem.theme.contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewBadgeColorsTest {
    @Test
    fun `badge text reaches 4_5 to 1 on its chip in both themes`() {
        for (source in ReviewBadgeSource.entries) {
            for (dark in listOf(false, true)) {
                val (background, text) = source.chipColors(dark)
                assertEquals("$source dark=$dark: opaque chip", 1f, background.alpha, 0f)
                val ratio = contrast(text, background)
                assertTrue("$source dark=$dark: $ratio", ratio >= 4.5)
            }
        }
    }
}
