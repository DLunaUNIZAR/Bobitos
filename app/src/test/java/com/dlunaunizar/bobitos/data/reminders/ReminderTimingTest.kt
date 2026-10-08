package com.dlunaunizar.bobitos.data.reminders

import com.dlunaunizar.bobitos.core.model.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderTimingTest {
    @Test
    fun `fire time is brought forward by the lead offset`() {
        val at = Instant.parse("2026-07-25T14:00:00Z")
        assertEquals(at, reminderFireAt(at, ReminderLeadTime.AT_TIME))
        assertEquals(Instant.parse("2026-07-25T13:30:00Z"), reminderFireAt(at, ReminderLeadTime.MIN_30))
        assertEquals(Instant.parse("2026-07-24T14:00:00Z"), reminderFireAt(at, ReminderLeadTime.DAY_BEFORE))
    }

    @Test
    fun `meal reminder maps each slot to its time of day`() {
        val zone = ZoneId.of("UTC")
        val date = LocalDate.of(2026, 7, 25)
        assertEquals(LocalTime.of(9, 0), mealSlotTime(MealSlot.DESAYUNO))
        assertEquals(LocalTime.of(14, 0), mealSlotTime(MealSlot.COMIDA))
        assertEquals(LocalTime.of(21, 0), mealSlotTime(MealSlot.CENA))
        assertEquals(
            date.atTime(21, 0).atZone(zone).toInstant(),
            mealReminderInstant(date, MealSlot.CENA, zone),
        )
    }

    @Test
    fun `an all-day event is reminded at 9 on its own day, not at midnight`() {
        val zone = ZoneId.of("Europe/Madrid")
        val date = LocalDate.of(2026, 10, 9)
        val midnight = date.atStartOfDay(zone).toInstant()
        val reminder = eventReminderInstant(allDay = true, startAt = midnight, startDate = date, zone = zone)
        assertEquals(date.atTime(9, 0).atZone(zone).toInstant(), reminder)
        assertEquals(
            date.atTime(8, 30).atZone(zone).toInstant(),
            reminderFireAt(reminder, ReminderLeadTime.MIN_30),
        )
    }

    @Test
    fun `a timed event is reminded at its start`() {
        val startAt = Instant.parse("2026-10-09T16:00:00Z")
        assertEquals(
            startAt,
            eventReminderInstant(allDay = false, startAt = startAt, startDate = null, zone = ZoneId.of("UTC")),
        )
    }

    @Test
    fun `lead time name round-trips with a safe default`() {
        assertEquals(ReminderLeadTime.MIN_60, ReminderLeadTime.fromName("MIN_60"))
        assertEquals(ReminderLeadTime.AT_TIME, ReminderLeadTime.fromName(null))
        assertEquals(ReminderLeadTime.AT_TIME, ReminderLeadTime.fromName("garbage"))
    }
}
