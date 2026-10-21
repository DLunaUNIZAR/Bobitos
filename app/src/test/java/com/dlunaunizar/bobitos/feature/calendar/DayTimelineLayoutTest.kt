package com.dlunaunizar.bobitos.feature.calendar

import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DayTimelineLayoutTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val day = LocalDate.of(2026, 10, 22)

    private fun at(hour: Int, minute: Int = 0, date: LocalDate = day): LocalDateTime = date.atTime(hour, minute)

    private fun rows(events: List<CalendarEvent>, forDay: LocalDate = day): List<List<Any>> =
        layoutDayEvents(events, forDay, zone).map {
            listOf(it.event.id, it.topMinute, it.bottomMinute, it.column, it.columns)
        }

    @Test
    fun `an event spans its real duration`() {
        assertEquals(
            listOf(listOf<Any>("ingles", 990, 1110, 0, 1)),
            rows(listOf(event("ingles", at(16, 30), at(18, 30)))),
        )
    }

    private val overlapping = listOf(
        event("ingles", at(16, 30), at(18, 0)),
        event("cine", at(18, 30), at(21, 30)),
        event("padel", at(20, 0), at(21, 0)),
    )

    @Test
    fun `overlapping events share the width and reuse free columns`() {
        assertEquals(
            listOf(
                listOf<Any>("ingles", 990, 1080, 0, 1),
                listOf<Any>("cine", 1110, 1290, 0, 2),
                listOf<Any>("padel", 1200, 1260, 1, 2),
            ),
            rows(overlapping),
        )
    }

    @Test
    fun `a chain of overlaps forms one group`() {
        assertEquals(
            listOf(
                listOf<Any>("a", 540, 660, 0, 2),
                listOf<Any>("b", 570, 615, 1, 2),
                listOf<Any>("c", 630, 690, 1, 2),
            ),
            rows(
                listOf(
                    event("a", at(9), at(11)),
                    event("b", at(9, 30), at(10)),
                    event("c", at(10, 30), at(11, 30)),
                ),
            ),
        )
    }

    @Test
    fun `equal starts put the longer event first`() {
        assertEquals(
            listOf(listOf<Any>("y", 540, 720, 0, 2), listOf<Any>("x", 540, 600, 1, 2)),
            rows(listOf(event("x", at(9), at(10)), event("y", at(9), at(12)))),
        )
    }

    @Test
    fun `short events take the minimum height and push overlaps aside`() {
        assertEquals(
            listOf(listOf<Any>("p", 600, 645, 0, 2), listOf<Any>("q", 630, 675, 1, 2)),
            rows(listOf(event("p", at(10), at(10, 15)), event("q", at(10, 30), at(11)))),
        )
    }

    @Test
    fun `a short event at the end of the day moves up to fit`() {
        assertEquals(
            listOf(listOf<Any>("late", 1395, 1440, 0, 1)),
            rows(listOf(event("late", at(23, 50), at(23, 59)))),
        )
    }

    @Test
    fun `an event crossing midnight is clipped to each day`() {
        val night = listOf(event("night", at(22, date = day.minusDays(1)), at(2)))
        assertEquals(listOf(listOf<Any>("night", 1320, 1440, 0, 1)), rows(night, day.minusDays(1)))
        assertEquals(listOf(listOf<Any>("night", 0, 120, 0, 1)), rows(night, day))
    }

    private val ignored = listOf(
        event("allday", at(0), at(0, date = day.plusDays(1)), allDay = true),
        event("tomorrow", at(10, date = day.plusDays(1)), at(11, date = day.plusDays(1))),
    )

    @Test
    fun `all-day events and events of other days are left out`() {
        assertEquals(emptyList<List<Any>>(), rows(ignored))
    }

    @Test
    fun `firstTimedHour is the hour of the first event or null`() {
        assertEquals(16, firstTimedHour(overlapping, day, zone))
        assertNull(firstTimedHour(ignored, day, zone))
    }

    private fun event(id: String, start: LocalDateTime, end: LocalDateTime, allDay: Boolean = false) = CalendarEvent(
        id = id,
        title = id,
        description = null,
        allDay = allDay,
        startAt = start.atZone(zone).toInstant(),
        endAt = end.atZone(zone).toInstant(),
        startDate = if (allDay) start.toLocalDate() else null,
        endDateExclusive = if (allDay) end.toLocalDate() else null,
        timeZone = "Europe/Madrid",
        color = EventColor.BLUE,
        participantIds = emptyList(),
        participantNames = emptyList(),
        createdBy = "david",
        createdByName = "David",
        createdAt = Instant.EPOCH,
        updatedBy = "david",
        updatedAt = Instant.EPOCH,
    )
}
