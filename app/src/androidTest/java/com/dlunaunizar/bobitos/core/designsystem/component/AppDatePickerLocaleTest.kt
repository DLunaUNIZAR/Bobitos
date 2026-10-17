package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class AppDatePickerLocaleTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun datePickerIsInSpanishEvenWithADeviceInEnglish() {
        rule.setContent { AppDatePickerDialog(LocalDate.of(2026, 10, 17), {}, {}) }
        rule.waitForIdle()
        val spanish = rule.onAllNodesWithText("octubre", substring = true, ignoreCase = true).fetchSemanticsNodes()
        assertTrue("el selector no sale en español", spanish.isNotEmpty())
        rule.onAllNodesWithText("October", substring = true).assertCountEquals(0)
    }
}
