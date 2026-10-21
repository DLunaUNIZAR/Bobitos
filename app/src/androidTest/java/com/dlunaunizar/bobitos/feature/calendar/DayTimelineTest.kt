package com.dlunaunizar.bobitos.feature.calendar

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class DayTimelineTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val day = LocalDate.of(2026, 10, 22)

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = day): Instant =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant()

    private fun event(id: String, title: String, start: Instant, end: Instant) = CalendarEvent(
        id = id,
        title = title,
        description = null,
        allDay = false,
        startAt = start,
        endAt = end,
        startDate = null,
        endDateExclusive = null,
        timeZone = ZoneId.systemDefault().id,
        color = EventColor.TEAL,
        participantIds = emptyList(),
        participantNames = emptyList(),
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
    )

    private fun setGrid(events: List<CalendarEvent>, canWrite: Boolean = false) {
        compose.setContent {
            BobitosTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    DayHourGrid(
                        events = events,
                        tasks = emptyList(),
                        canWrite = canWrite,
                        onEdit = {},
                        onDelete = {},
                        onCreateAt = {},
                        day = day,
                    )
                }
            }
        }
    }

    private fun hasChildText(text: String) = SemanticsMatcher("has text $text") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
    }

    private fun bounds(id: String): DpRect = compose.onNodeWithTag(timelineEventTag(id)).getUnclippedBoundsInRoot()

    private fun assertNear(expected: Dp, actual: Dp, tolerance: Dp) {
        assertTrue("esperado $expected, fue $actual", (expected - actual).value in -tolerance.value..tolerance.value)
    }

    @Test fun aTimedEventIsAsTallAsItsDuration() {
        setGrid(
            listOf(
                event("a", "Inglés", at(16, 30), at(18, 30)),
                event("b", "Corto", at(9, 0), at(9, 45)),
            ),
        )
        assertNear(128.dp, bounds("a").height, 4.dp)
        assertNear(48.dp, bounds("b").height, 4.dp)
    }

    @Test fun overlappingEventsSitSideBySide() {
        setGrid(
            listOf(
                event("c", "Cine", at(18, 30), at(21, 30)),
                event("p", "Pádel", at(20, 0), at(21, 0)),
            ),
        )
        val cine = bounds("c")
        val padel = bounds("p")
        assertNear(cine.width, padel.width, 1.dp)
        assertTrue("cine ${cine.width}", cine.width >= 120.dp)
        assertTrue("padel ${padel.width}", padel.width >= 120.dp)
        assertTrue(cine.right <= padel.left + 1.dp)
    }

    @Test fun theCardShowsTheTimeRange() {
        setGrid(listOf(event("a", "Inglés", at(16, 30), at(18, 30))))
        compose.onNodeWithTag(timelineEventTag("a")).assert(
            SemanticsMatcher("shows the range") { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "16:30 – 18:30" } == true
            },
        )
    }

    @Test fun holdingAnEmptySpotCreatesAtThatHour() {
        val created = mutableListOf<LocalTime>()
        val edited = mutableListOf<String>()
        val english = event("a", "Inglés", at(16, 30), at(18, 30))
        compose.setContent {
            BobitosTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    DayHourGrid(
                        events = listOf(english),
                        tasks = emptyList(),
                        canWrite = true,
                        onEdit = { edited += it.id },
                        onDelete = {},
                        onCreateAt = { created += it },
                        day = day,
                    )
                }
            }
        }
        compose.onNode(hasChildText("10:00")).performScrollTo().performTouchInput { longClick(center) }
        compose.waitForIdle()
        assertEquals(listOf(LocalTime.of(10, 0)), created)
        compose.onNodeWithTag(timelineEventTag("a")).performClick()
        compose.waitForIdle()
        assertEquals(listOf("a"), edited)
        assertEquals(1, created.size)
    }

    @Test fun theDayOpensAtTheFirstEventOnlyOnce() {
        var shown by mutableStateOf(day)
        val events = mutableStateListOf(event("a", "Inglés", at(16, 30), at(18, 30)))
        compose.setContent {
            BobitosTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    DayHourGrid(
                        events = events.toList(),
                        tasks = emptyList(),
                        canWrite = false,
                        onEdit = {},
                        onDelete = {},
                        onCreateAt = {},
                        day = shown,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("16:00").assertIsDisplayed()
        compose.onNodeWithText("00:00").assertIsNotDisplayed()

        events += event("b", "Madrugón", at(7, 0), at(8, 0))
        compose.waitForIdle()
        compose.onNodeWithText("16:00").assertIsDisplayed()

        shown = day.plusDays(1)
        compose.waitForIdle()
        compose.onNodeWithText("08:00").assertIsDisplayed()
        compose.onNodeWithText("07:00").assertIsNotDisplayed()
    }
}
