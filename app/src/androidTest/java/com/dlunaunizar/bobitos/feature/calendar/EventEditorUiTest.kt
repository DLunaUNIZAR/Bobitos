package com.dlunaunizar.bobitos.feature.calendar

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.BobitosDates
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class EventEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val day = LocalDate.of(2026, 10, 15)

    private fun text(id: Int) = compose.activity.getString(id)

    private fun dateText(date: LocalDate) = BobitosDates.shortDate(date)

    private fun pickEndDate(target: LocalDate, current: LocalDate) {
        // Los dos botones de fecha (inicio y fin) muestran el mismo texto al abrir: el fin es el último.
        compose.onAllNodesWithText(dateText(current)).onLast().performClick()
        // Cada día del selector se anuncia con su fecha completa («Sábado, 10 de octubre de 2026»).
        val spoken = BobitosDates.accessibleDate(target)
        compose.onNode(hasClickAction() and hasText(spoken, substring = true, ignoreCase = true)).performClick()
        compose.onNodeWithText(text(R.string.accept)).performClick()
    }

    @Test fun theDateErrorClearsWhenTheDatesAreFixed() {
        compose.setContent {
            BobitosTheme {
                EventEditor(
                    event = null,
                    day = day,
                    initialStart = null,
                    members = emptyList(),
                    saving = false,
                    errorMessage = null,
                    canWrite = true,
                    dismiss = {},
                    save = { _, _ -> },
                )
            }
        }
        compose.onAllNodesWithText(text(R.string.calendar_date_error)).assertCountEquals(0)

        val before = day.minusDays(5)
        pickEndDate(before, day)
        compose.onNodeWithText(text(R.string.calendar_save)).performClick()
        compose.onNodeWithText(text(R.string.calendar_date_error)).assertIsDisplayed()

        pickEndDate(day, before)
        compose.onAllNodesWithText(text(R.string.calendar_date_error)).assertCountEquals(0)
    }
}
