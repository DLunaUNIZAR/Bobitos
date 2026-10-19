package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Date

class CalendarEventFieldsTest {
    private val start = Instant.parse("2026-10-09T08:00:00Z")
    private val end = Instant.parse("2026-10-09T09:00:00Z")
    private val created = Instant.parse("2026-10-01T10:00:00Z")

    private fun ts(instant: Instant) = Timestamp(Date.from(instant))

    private fun fields(): Map<String, Any?> = mapOf(
        "title" to "Correr",
        "description" to "Por el parque",
        "allDay" to false,
        "startAt" to ts(start),
        "endAt" to ts(end),
        "startDate" to null,
        "endDateExclusive" to null,
        "timeZone" to "Europe/Madrid",
        "color" to "BLUE",
        "participantIds" to listOf("ana", "luis"),
        "participantNames" to listOf("Ana", "Luis"),
        "createdBy" to "ana",
        "createdByName" to "Ana",
        "createdAt" to ts(created),
        "updatedBy" to "luis",
        "updatedAt" to ts(created.plusSeconds(60)),
    )

    @Test
    fun `a well formed event is read`() {
        val event = calendarEventOf("e1", fields())

        assertEquals(
            CalendarEvent(
                "e1", "Correr", "Por el parque", false, start, end, null, null, "Europe/Madrid", EventColor.BLUE,
                listOf("ana", "luis"), listOf("Ana", "Luis"), "ana", "Ana", created, "luis",
                created.plusSeconds(60),
            ),
            event,
        )
    }

    @Test
    fun `updatedAt falls back to createdAt`() {
        assertEquals(created, calendarEventOf("e1", fields() - "updatedAt")?.updatedAt)
    }

    @Test
    fun `a missing required field gives null`() {
        listOf(
            "title", "allDay", "startAt", "endAt", "timeZone", "createdBy", "createdByName", "createdAt", "updatedBy",
        ).forEach { assertNull(it, calendarEventOf("e1", fields() - it)) }
    }

    @Test
    fun `a malformed startDate gives null instead of throwing`() {
        assertNull(calendarEventOf("e1", fields() + ("startDate" to "no-es-fecha")))
        assertNull(calendarEventOf("e1", fields() + ("endDateExclusive" to "2026-13-45")))
    }

    @Test
    fun `an unknown color gives null`() {
        assertNull(calendarEventOf("e1", fields() + ("color" to "FUCSIA")))
        assertNull(calendarEventOf("e1", fields() - "color"))
    }

    @Test
    fun `toInput keeps everything the input needs`() {
        val event = calendarEventOf(
            "e1",
            fields() + mapOf("allDay" to true, "startDate" to "2026-10-09", "endDateExclusive" to "2026-10-10"),
        )
        assertNotNull(event)

        assertEquals(
            EventInput(
                title = "Correr",
                description = "Por el parque",
                allDay = true,
                startAt = start,
                endAt = end,
                startDate = LocalDate.parse("2026-10-09"),
                endDateExclusive = LocalDate.parse("2026-10-10"),
                timeZone = "Europe/Madrid",
                color = EventColor.BLUE,
                participantIds = listOf("ana", "luis"),
            ),
            event!!.toInput(),
        )
    }
}
