package com.dlunaunizar.bobitos.data.reminders

import com.dlunaunizar.bobitos.core.model.MealSlot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// Antelación con la que avisar antes de la hora del evento/tarea/comida.
enum class ReminderLeadTime(val offset: Duration) {
    AT_TIME(Duration.ZERO),
    MIN_10(Duration.ofMinutes(10)),
    MIN_30(Duration.ofMinutes(30)),
    MIN_60(Duration.ofHours(1)),
    DAY_BEFORE(Duration.ofDays(1)),
    ;

    companion object {
        fun fromName(value: String?): ReminderLeadTime = entries.firstOrNull { it.name == value } ?: AT_TIME
    }
}

// Momento en que disparar el recordatorio, adelantándolo según la antelación elegida.
internal fun reminderFireAt(at: Instant, lead: ReminderLeadTime): Instant = at.minus(lead.offset)

// Hora del día asociada a cada franja (las comidas no tienen hora exacta).
internal fun mealSlotTime(slot: MealSlot): LocalTime = when (slot) {
    MealSlot.DESAYUNO -> LocalTime.of(9, 0)
    MealSlot.COMIDA -> LocalTime.of(14, 0)
    MealSlot.CENA -> LocalTime.of(21, 0)
}

internal fun mealReminderInstant(date: LocalDate, slot: MealSlot, zone: ZoneId): Instant =
    date.atTime(mealSlotTime(slot)).atZone(zone).toInstant()
