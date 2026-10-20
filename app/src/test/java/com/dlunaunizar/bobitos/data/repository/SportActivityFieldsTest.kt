package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import com.dlunaunizar.bobitos.core.model.SportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SportActivityFieldsTest {
    private val me = AuthUser(id = "me", displayName = "Yo", email = "yo@example.com", isEmailVerified = true)
    private val date = LocalDate.of(2026, 10, 19)
    private val gym = GymSession(SportType.PADEL, null, emptyList())

    private fun data(done: Boolean, eventId: String?) =
        activityData(me, date, SportType.PADEL, "Pádel", listOf("a"), listOf("Ana"), gym, eventId, done)

    @Test
    fun `a restored done activity is written done in the same write`() {
        val restored = data(done = true, eventId = "e1")

        assertEquals(true, restored["done"])
        assertEquals("e1", restored["eventId"])
        assertEquals("me", restored["createdBy"])
        assertEquals("me", restored["updatedBy"])
    }

    @Test
    fun `an activity restored without its event has no eventId`() {
        val restored = data(done = false, eventId = null)

        assertNull(restored["eventId"])
        assertTrue(restored.containsKey("eventId"))
    }

    @Test
    fun `a new activity is written not done`() {
        val added = data(done = false, eventId = "new")

        assertEquals(false, added["done"])
        assertEquals(listOf("a"), added["participantIds"])
        assertEquals(listOf("Ana"), added["participantNames"])
    }

    @Test
    fun `participants who left are dropped from the activity and its event`() {
        val records = listOf(
            MembershipRecord("a", "ACTIVE", "Ana"),
            MembershipRecord("b", "REMOVED", "Berta"),
            null,
            MembershipRecord("d", "ACTIVE", "Dani"),
        )
        val active = activeMembers(listOf("a", "b", "c", "d"), records)
        val event = linkedEvent(participantIds = listOf("a", "b", "c", "d"))

        val activity = activityData(
            me,
            date,
            SportType.PADEL,
            "Pádel",
            active.map(ActiveMember::id),
            active.map(ActiveMember::name),
            gym,
            event.id,
            false,
        )
        val restoredEvent = eventDataForRestore(me, event, active)

        assertEquals(listOf("a", "d"), activity["participantIds"])
        assertEquals(listOf("Ana", "Dani"), activity["participantNames"])
        assertEquals(listOf("a", "d"), restoredEvent["participantIds"])
        assertEquals(listOf("Ana", "Dani"), restoredEvent["participantNames"])
        assertEquals("Pádel (editado)", restoredEvent["title"])
        assertEquals("me", restoredEvent["createdBy"])
    }

    @Test
    fun `the organizer stays in the restored event although they do not take part in the activity`() {
        val records = listOf(MembershipRecord("a", "ACTIVE", "Ana"), MembershipRecord("owner", "ACTIVE", "David"))
        val active = activeMembers(listOf("a", "owner"), records)
        val event = linkedEvent(participantIds = listOf("a", "owner"))
        val activityActive = activeMembers(listOf("a"), records)

        val activity = activityData(
            me,
            date,
            SportType.PADEL,
            "Pádel",
            activityActive.map(ActiveMember::id),
            activityActive.map(ActiveMember::name),
            gym,
            event.id,
            false,
        )
        val restoredEvent = eventDataForRestore(me, event, active)

        assertEquals(listOf("a"), activity["participantIds"])
        assertEquals(listOf("a", "owner"), restoredEvent["participantIds"])
        assertEquals(listOf("Ana", "David"), restoredEvent["participantNames"])
    }

    private fun linkedEvent(participantIds: List<String>) = CalendarEvent(
        id = "e1",
        title = "Pádel (editado)",
        description = null,
        allDay = true,
        startAt = Instant.EPOCH,
        endAt = Instant.EPOCH.plusSeconds(86_400),
        startDate = LocalDate.of(1970, 1, 1),
        endDateExclusive = LocalDate.of(1970, 1, 2),
        timeZone = "UTC",
        color = EventColor.BLUE,
        participantIds = participantIds,
        participantNames = participantIds,
        createdBy = "owner",
        createdByName = "David",
        createdAt = Instant.EPOCH,
        updatedBy = "owner",
        updatedAt = Instant.EPOCH,
    )
}
