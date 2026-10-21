package com.dlunaunizar.bobitos.feature.calendar

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskStatus
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

    @Test fun arrowsOpenTheNewDayAtItsFirstEventWhenItsEventsArrive() {
        var shown by mutableStateOf(day)
        var ready by mutableStateOf(true)
        var events by mutableStateOf(listOf(event("a", "Inglés", at(16, 30), at(18, 30))))
        compose.setContent {
            BobitosTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    DayHourGrid(
                        events = events,
                        tasks = emptyList(),
                        canWrite = false,
                        onEdit = {},
                        onDelete = {},
                        onCreateAt = {},
                        day = shown,
                        ready = ready,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("16:00").assertIsDisplayed()

        // Con las flechas el día cambia primero y sus eventos llegan después; entretanto siguen los del día anterior.
        ready = false
        shown = day.plusDays(1)
        compose.waitForIdle()
        val next = day.plusDays(1)
        events = listOf(event("c", "Cena", at(20, 0, next), at(21, 0, next)))
        ready = true
        compose.waitForIdle()
        compose.onNodeWithText("20:00").assertIsDisplayed()
        compose.onNodeWithText("08:00").assertIsNotDisplayed()
    }

    @Test fun narrowOverlappingCardsKeepTitleAndTimeReadable() {
        val deleted = mutableListOf<String>()
        val titles = listOf(
            "Fútbol del peque" to "f",
            "Inglés con Marta" to "i",
            "Dentista de Ana" to "d",
            "Compra semanal" to "c",
        )
        val list = titles.map { (title, id) -> event(id, title, at(17, 0), at(19, 0)) }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                BobitosTheme {
                    Box(Modifier.size(width = 328.dp, height = 640.dp)) {
                        DayHourGrid(
                            events = list,
                            tasks = emptyList(),
                            canWrite = true,
                            onEdit = {},
                            onDelete = { deleted += it },
                            onCreateAt = {},
                            day = day,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        titles.forEach { (title, id) ->
            compose.onNodeWithContentDescription("Más opciones de «$title»").assertDoesNotExist()
            val text = compose.onNode(hasText(title) and hasAnyAncestor(hasTestTag(timelineEventTag(id))), true)
            val box = text.getUnclippedBoundsInRoot()
            assertTrue("texto de $title: ${box.width}", box.width >= 40.dp)
            assertTrue("título de $title en una línea: ${box.height}", box.height < 40.dp)
            compose.onNodeWithTag(timelineEventTag(id)).assert(
                SemanticsMatcher("has TalkBack edit and delete") { node ->
                    val labels = node.config.getOrNull(SemanticsActions.CustomActions)?.map { it.label }
                    labels?.containsAll(listOf("Editar", "Eliminar")) == true
                },
            )
        }
        compose.onNodeWithTag(timelineEventTag("i")).performTouchInput { longClick(center) }
        compose.onNodeWithText("Eliminar").performClick()
        compose.waitForIdle()
        assertEquals(listOf("i"), deleted)
    }

    @Test fun aSingleEventKeepsItsMenuButton() {
        setGrid(listOf(event("a", "Inglés", at(16, 30), at(18, 30))), canWrite = true)
        compose.onNodeWithContentDescription("Más opciones de «Inglés»").assertIsDisplayed()
    }

    @Test fun allDayBlockStaysVisibleWhenTheDayOpensAtTheFirstEvent() {
        val birthday = event("b", "Cumpleaños de Ana", at(0), at(0, date = day.plusDays(1)))
            .copy(allDay = true, startDate = day, endDateExclusive = day.plusDays(1))
        val task = TaskItem(
            id = "t",
            title = "Llamar al dentista",
            description = null,
            assigneeId = null,
            assigneeName = null,
            dueAt = at(12),
            priority = TaskPriority.MEDIUM,
            status = TaskStatus.TODO,
            createdBy = "u",
            createdByName = "U",
            createdAt = Instant.EPOCH,
            updatedBy = "u",
            updatedAt = Instant.EPOCH,
            completedBy = null,
            completedByName = null,
            completedAt = null,
        )
        compose.setContent {
            BobitosTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    DayHourGrid(
                        events = listOf(birthday, event("a", "Inglés", at(16, 30), at(18, 30))),
                        tasks = listOf(task),
                        canWrite = true,
                        onEdit = {},
                        onDelete = {},
                        onCreateAt = {},
                        day = day,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Cumpleaños de Ana").assertIsDisplayed()
        compose.onNodeWithText("Llamar al dentista").assertIsDisplayed()
        compose.onNodeWithText("16:00").assertIsDisplayed()
    }

    @Test fun aLongEventShowsItsTitleAtTheTop() {
        setGrid(listOf(event("v", "Viaje a Asturias", at(0), at(12))))
        compose.waitForIdle()
        val card = bounds("v")
        val title = compose
            .onNode(hasText("Viaje a Asturias") and hasAnyAncestor(hasTestTag(timelineEventTag("v"))), true)
            .getUnclippedBoundsInRoot()
        assertTrue("título a ${title.top - card.top} de la parte alta", title.top - card.top < 24.dp)
    }

    @Test fun theOpenMenuStaysWithItsEventWhenAnEarlierOneArrives() {
        val deleted = mutableListOf<String>()
        var events by mutableStateOf(listOf(event("cine", "Cine", at(18, 30), at(20, 0))))
        compose.setContent {
            BobitosTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    DayHourGrid(
                        events = events,
                        tasks = emptyList(),
                        canWrite = true,
                        onEdit = {},
                        onDelete = { deleted += it },
                        onCreateAt = {},
                        day = day,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Más opciones de «Cine»").performClick()
        events = listOf(event("des", "Desayuno", at(8, 0), at(9, 0))) + events
        compose.waitForIdle()
        compose.onNodeWithText("Eliminar").performClick()
        compose.waitForIdle()
        assertEquals(listOf("cine"), deleted)
    }

    private var wideEvents by mutableStateOf(emptyList<CalendarEvent>())
    private var wideScale by mutableStateOf(1f)

    /** Monta una vez la vista a 328 dp; los eventos y la letra se cambian después con [wideEvents] y [wideScale]. */
    private fun setWide(events: List<CalendarEvent>, fontScale: Float = 1f) {
        wideEvents = events
        wideScale = fontScale
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = wideScale),
            ) {
                BobitosTheme {
                    Box(Modifier.size(width = 328.dp, height = 640.dp)) {
                        DayHourGrid(
                            events = wideEvents,
                            tasks = emptyList(),
                            canWrite = true,
                            onEdit = {},
                            onDelete = {},
                            onCreateAt = {},
                            day = day,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun sameTime(count: Int) = (0 until count).map { event("e$it", "Evento $it", at(10, 0), at(11, 0)) }

    @Test fun aSharedCardNeverCutsATime() {
        setWide(listOf(event("a", "Reunión", at(10, 0), at(11, 0)), event("b", "Dentista", at(10, 30), at(11, 30))))
        listOf(1f, 1.3f).forEach { scale ->
            wideScale = scale
            compose.waitForIdle()
            listOf("a" to ("10:00 – 11:00" to "10:00"), "b" to ("10:30 – 11:30" to "10:30")).forEach { (id, times) ->
                val node = compose.onNode(
                    (
                        hasText(
                            times.first,
                        ) or hasText(times.second)
                        ) and hasAnyAncestor(hasTestTag(timelineEventTag(id))),
                    useUnmergedTree = true,
                )
                val results = mutableListOf<TextLayoutResult>()
                node.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!(results)
                val layout = results.single()
                assertTrue("recortado en $id con letra $scale: ${layout.layoutInput.text}", !layout.isLineEllipsized(0))
                assertEquals("líneas en $id con letra $scale", 1, layout.lineCount)
            }
        }
    }

    @Test fun everyMenuButtonIsAtLeast48dp() {
        setWide(sameTime(1))
        compose.onNodeWithContentDescription("Más opciones de «Evento 0»").assertExists()
        listOf(1, 2, 3).forEach { count ->
            wideEvents = sameTime(count)
            compose.waitForIdle()
            (0 until count).forEach { i ->
                val menu = compose.onAllNodesWithContentDescription("Más opciones de «Evento $i»")
                if (menu.fetchSemanticsNodes().isNotEmpty()) {
                    val box = menu[0].getUnclippedBoundsInRoot()
                    assertTrue(
                        "⋮ de $count columnas: ${box.width} x ${box.height}",
                        box.width >= 48.dp && box.height >= 48.dp,
                    )
                }
            }
        }
    }

    @Test fun aCompactCardAnnouncesItsFullRange() {
        setWide(sameTime(4), fontScale = 1.3f)
        (0 until 4).forEach { i ->
            // Con permiso de escritura, el nodo fusionado que TalkBack lee es el de la zona pulsable de la tarjeta.
            compose.onNode(hasText("Evento $i") and hasAnyAncestor(hasTestTag(timelineEventTag("e$i")))).assert(
                SemanticsMatcher("announces title and full range") { node ->
                    val spoken = (
                        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                        )
                    spoken.any { it == "Evento $i" } && spoken.any { it.contains("10:00 – 11:00") }
                },
            )
        }
    }
}
