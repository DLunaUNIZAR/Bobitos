package com.dlunaunizar.bobitos.feature.calendar

import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarFilteringTest {
    private val general = event("general", emptyList())
    private val david = event("david", listOf("david"))
    private val shared = event("shared", listOf("david", "ana"))
    private val ana = event("ana", listOf("ana"))
    private val events = listOf(general, david, shared, ana)

    @Test
    fun `space filter keeps general events and events for selected members`() {
        assertEquals(
            listOf("general", "david", "shared"),
            events.forSelectedMembers(setOf("david")).map(CalendarEvent::id),
        )
    }

    @Test
    fun `space filter with no members keeps only general events`() {
        assertEquals(
            listOf("general"),
            events.forSelectedMembers(emptySet()).map(CalendarEvent::id),
        )
    }

    @Test
    fun `personal calendar only keeps events where user participates`() {
        assertEquals(
            listOf("david", "shared"),
            events.forParticipant("david").map(CalendarEvent::id),
        )
    }

    @Test
    fun `personal aggregation keeps participating events and their spaces`() {
        val spaces = listOf(
            SpaceSummary("home", "Casa", 2, SpaceRole.OWNER),
            SpaceSummary("work", "Trabajo", 3, SpaceRole.MEMBER),
        )

        val result = aggregatePersonalEvents(
            userId = "david",
            spaces = spaces,
            eventsBySpace = listOf(
                listOf(general, david),
                listOf(ana, shared),
            ),
        )

        assertEquals(listOf("Casa", "Trabajo"), result.map(PersonalCalendarEvent::spaceName))
        assertEquals(listOf("david", "shared"), result.map { it.event.id })
    }

    private fun event(id: String, participants: List<String>) = CalendarEvent(
        id = id,
        title = id,
        description = null,
        allDay = false,
        startAt = Instant.parse("2026-07-17T10:00:00Z"),
        endAt = Instant.parse("2026-07-17T11:00:00Z"),
        startDate = null,
        endDateExclusive = null,
        timeZone = "Europe/Madrid",
        color = EventColor.BLUE,
        participantIds = participants,
        participantNames = participants,
        createdBy = "david",
        createdByName = "David",
        createdAt = Instant.EPOCH,
        updatedBy = "david",
        updatedAt = Instant.EPOCH,
    )
}
