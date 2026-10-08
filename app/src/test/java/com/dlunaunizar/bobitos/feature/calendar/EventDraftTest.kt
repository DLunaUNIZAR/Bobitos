package com.dlunaunizar.bobitos.feature.calendar

import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class EventDraftTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val day = LocalDate.of(2026, 10, 7)
    private fun event(allDay: Boolean) = CalendarEvent(
        id = "e1",
        title = "Cena",
        description = "con amigos",
        allDay = allDay,
        startAt = LocalDate.of(2026, 10, 9).atTime(21, 30).atZone(zone).toInstant(),
        endAt = LocalDate.of(2026, 10, 9).atTime(23, 0).atZone(zone).toInstant(),
        startDate = if (allDay) LocalDate.of(2026, 10, 9) else null,
        endDateExclusive = if (allDay) LocalDate.of(2026, 10, 11) else null,
        timeZone = zone.id,
        color = EventColor.GREEN,
        participantIds = listOf("u2", "u1"),
        participantNames = listOf("B", "A"),
        createdBy = "u1",
        createdByName = "A",
        createdAt = Instant.EPOCH,
        updatedBy = "u1",
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun unEventoNuevoSinHoraEmpiezaTodoElDiaEnElDiaEnfocado() {
        val draft = EventDraft.of(null, day, null, zone)
        assertEquals(true, draft.allDay)
        assertEquals(day, draft.startDate)
        assertEquals(day, draft.endDate)
        assertEquals(LocalTime.of(9, 0), draft.startTime)
        assertEquals(LocalTime.of(10, 0), draft.endTime)
        assertEquals(EventColor.BLUE, draft.color)
    }

    @Test
    fun unEventoNuevoCreadoDesdeUnaHoraDuraUnaHora() {
        val draft = EventDraft.of(null, day, LocalTime.of(18, 0), zone)
        assertEquals(false, draft.allDay)
        assertEquals(LocalTime.of(18, 0), draft.startTime)
        assertEquals(LocalTime.of(19, 0), draft.endTime)
    }

    @Test
    fun unEventoConHoraMuestraSusFechasYHorasEnLaZonaLocal() {
        val draft = EventDraft.of(event(allDay = false), day, null, zone)
        assertEquals(false, draft.allDay)
        assertEquals(LocalDate.of(2026, 10, 9), draft.startDate)
        assertEquals(LocalDate.of(2026, 10, 9), draft.endDate)
        assertEquals(LocalTime.of(21, 30), draft.startTime)
        assertEquals(LocalTime.of(23, 0), draft.endTime)
        assertEquals(EventColor.GREEN, draft.color)
        assertEquals(setOf("u1", "u2"), draft.selectedIds.toSet())
    }

    @Test
    fun unEventoDeTodoElDiaMuestraElUltimoDiaInclusivo() {
        val draft = EventDraft.of(event(allDay = true), day, null, zone)
        assertEquals(true, draft.allDay)
        assertEquals(LocalDate.of(2026, 10, 9), draft.startDate)
        assertEquals(LocalDate.of(2026, 10, 10), draft.endDate)
    }
}
