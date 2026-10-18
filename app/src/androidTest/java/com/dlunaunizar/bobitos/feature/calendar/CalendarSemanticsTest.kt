package com.dlunaunizar.bobitos.feature.calendar

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CalendarSemanticsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 10, 17)

    private val event = CalendarEvent(
        id = "e1",
        title = "Cena con Ana",
        description = null,
        allDay = false,
        startAt = Instant.parse("2026-10-17T19:00:00Z"),
        endAt = Instant.parse("2026-10-17T20:00:00Z"),
        startDate = null,
        endDateExclusive = null,
        timeZone = "UTC",
        color = EventColor.TEAL,
        participantIds = emptyList(),
        participantNames = emptyList(),
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
    )

    private fun hasRadioRole() = SemanticsMatcher("is a radio button") {
        it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton
    }

    @Test fun laCeldaDelMesEsUnaOpcionUnicaConHoy() {
        compose.setContent {
            BobitosTheme {
                androidx.compose.foundation.layout.Column {
                    DayCell(today, emptyList(), isSelected = true, isToday = true, inMonth = true, onClick = {})
                    DayCell(
                        today.minusDays(1),
                        emptyList(),
                        isSelected = false,
                        isToday = false,
                        inMonth = true,
                        onClick = {},
                    )
                }
            }
        }
        val todayCell = compose.onNode(hasContentDescriptionContaining("17 de octubre"))
        todayCell.assert(hasRadioRole())
        todayCell.assertIsSelected()
        todayCell.assert(hasContentDescriptionContaining("hoy"))
        val other = compose.onNode(hasContentDescriptionContaining("16 de octubre"))
        other.assert(hasRadioRole())
        other.assertIsNotSelected()
        other.assert(SemanticsMatcher("no today") { !descriptionOf(it).contains("hoy") })
    }

    @Test fun lasCabecerasDeDiaDeLaSemanaSonEncabezados() {
        compose.setContent {
            BobitosTheme {
                WeekEventList(
                    focusedDate = today,
                    events = listOf(event),
                    canWrite = true,
                    onEdit = {},
                    onDelete = {},
                    onDayTap = {},
                )
            }
        }
        compose.onNode(hasChildText("Sábado 17")).assert(
            SemanticsMatcher("is a heading") {
                it.config.getOrNull(SemanticsProperties.Heading) != null
            },
        )
    }

    @Test fun anHourRowIsNotAnEmptyButton() {
        var writable by androidx.compose.runtime.mutableStateOf(false)
        var created = 0
        compose.setContent {
            BobitosTheme {
                HourRow(
                    hour = 10,
                    events = emptyList(),
                    canWrite = writable,
                    onEdit = {},
                    onDelete = {},
                    onLongPress = { created++ },
                )
            }
        }
        compose.onNodeWithText("10:00").assertIsDisplayed()
        val timeNode = compose.onNode(hasChildText("10:00"))
        timeNode.assert(SemanticsMatcher("no click") { SemanticsActions.OnClick !in it.config })
        timeNode.assert(SemanticsMatcher("no actions") { SemanticsActions.CustomActions !in it.config })
        writable = true
        compose.waitForIdle()
        val action = compose.onNode(hasChildText("10:00")).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single()
        timeNode.assert(SemanticsMatcher("no click") { SemanticsActions.OnClick !in it.config })
        assertTrue(action.label.contains("10:00"))
        compose.runOnUiThread { assertTrue(action.action()) }
        assertEquals(1, created)
    }

    @Test fun anEventRowNamesItsMenu() {
        compose.setContent { BobitosTheme { EventRow(event, canWrite = true, edit = {}, delete = {}) } }
        compose.onNodeWithContentDescription("Más opciones de «Cena con Ana»").assertIsDisplayed()
    }

    @Test fun deletingAnEventIsDisabledWhileAnotherWriteIsInFlight() {
        var deleteEnabled by androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {
            BobitosTheme {
                EventRow(event, canWrite = true, edit = {}, delete = {}, deleteEnabled = deleteEnabled)
            }
        }
        compose.onNodeWithContentDescription("Más opciones de «Cena con Ana»").performClick()
        compose.onNodeWithText("Eliminar").assertIsNotEnabled()
        deleteEnabled = true
        compose.waitForIdle()
        compose.onNodeWithText("Eliminar").assertIsEnabled()
    }

    @Test fun anEventRowIsClickableOnlyWhenWritable() {
        var writable by androidx.compose.runtime.mutableStateOf(true)
        compose.setContent { BobitosTheme { EventRow(event, canWrite = writable, edit = {}, delete = {}) } }
        compose.onNode(hasChildText("Cena con Ana")).assertHasClickAction()
        writable = false
        compose.waitForIdle()
        compose.onNode(hasChildText("Cena con Ana")).assert(
            SemanticsMatcher("no click") { SemanticsActions.OnClick !in it.config },
        )
        compose.onNodeWithContentDescription("Más opciones de «Cena con Ana»").assertDoesNotExist()
    }

    private fun descriptionOf(node: androidx.compose.ui.semantics.SemanticsNode) =
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()

    private fun hasContentDescriptionContaining(text: String) =
        SemanticsMatcher("description contains $text") { descriptionOf(it).contains(text) }

    private fun hasChildText(text: String) = SemanticsMatcher("has text $text") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
    }
}
