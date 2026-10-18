package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DayCascadeUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val diaA = LocalDate.of(2026, 10, 12)
    private val diaB = LocalDate.of(2026, 10, 19)

    @Test
    fun alCargarOtroDiaTrasCargandoCascada() {
        composeTestRule.mainClock.autoAdvance = false
        var day by mutableStateOf(diaA)
        var ready by mutableStateOf(true)
        var cascade = false
        composeTestRule.setContent { cascade = rememberDayCascade(day, ready) }
        composeTestRule.mainClock.advanceTimeByFrame()
        assertFalse(cascade)

        ready = false
        day = diaB
        composeTestRule.mainClock.advanceTimeByFrame()
        assertFalse(cascade)

        ready = true
        composeTestRule.mainClock.advanceTimeByFrame()
        assertTrue(cascade)

        composeTestRule.mainClock.advanceTimeBy(CASCADE_WINDOW_MS + 100)
        assertFalse(cascade)
    }

    @Test
    fun laPrimeraCargaNoCascada() {
        composeTestRule.mainClock.autoAdvance = false
        var ready by mutableStateOf(false)
        var cascade = false
        composeTestRule.setContent { cascade = rememberDayCascade(diaA, ready) }
        composeTestRule.mainClock.advanceTimeByFrame()
        assertFalse(cascade)

        ready = true
        composeTestRule.mainClock.advanceTimeByFrame()
        assertFalse(cascade)
    }
}
