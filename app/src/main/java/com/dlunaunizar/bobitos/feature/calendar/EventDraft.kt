package com.dlunaunizar.bobitos.feature.calendar

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import kotlinx.parcelize.Parcelize
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// Borrador del editor de evento. Parcelable para que sobreviva a una rotación (fechas y horas de
// java.time viajan como Serializable).
@Parcelize
internal data class EventDraft(
    val title: String,
    val description: String,
    val allDay: Boolean,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val color: EventColor,
    val selectedIds: List<String>,
) : Parcelable {
    fun withParticipant(userId: String, selected: Boolean) = copy(
        selectedIds = if (selected) (selectedIds + userId).distinct() else selectedIds - userId,
    )

    companion object {
        /** Valores iniciales: los del evento al editar; al crear, el día enfocado y, si viene de una hora, 1 h de duración. */
        fun of(event: CalendarEvent?, day: LocalDate, initialStart: LocalTime?, zone: ZoneId) = EventDraft(
            title = event?.title.orEmpty(),
            description = event?.description.orEmpty(),
            allDay = event?.allDay ?: (initialStart == null),
            startDate = initialStartDate(event, day, zone),
            endDate = initialEndDate(event, day, zone),
            startTime = initialStartTime(event, initialStart, zone),
            endTime = initialEndTime(event, initialStart, zone),
            color = event?.color ?: EventColor.BLUE,
            selectedIds = event?.participantIds.orEmpty().distinct().sorted(),
        )

        private fun initialStartDate(event: CalendarEvent?, day: LocalDate, zone: ZoneId): LocalDate = when {
            event == null -> day
            event.allDay -> event.startDate!!
            else -> event.startAt.atZone(zone).toLocalDate()
        }

        private fun initialEndDate(event: CalendarEvent?, day: LocalDate, zone: ZoneId): LocalDate = when {
            event == null -> day
            event.allDay -> event.endDateExclusive!!.minusDays(1)
            else -> event.endAt.atZone(zone).toLocalDate()
        }

        private fun initialStartTime(event: CalendarEvent?, initialStart: LocalTime?, zone: ZoneId): LocalTime = when {
            event?.allDay == false -> event.startAt.atZone(zone).toLocalTime()
            initialStart != null -> initialStart
            else -> LocalTime.of(9, 0)
        }

        private fun initialEndTime(event: CalendarEvent?, initialStart: LocalTime?, zone: ZoneId): LocalTime = when {
            event?.allDay == false -> event.endAt.atZone(zone).toLocalTime()
            initialStart != null -> initialStart.plusHours(1)
            else -> LocalTime.of(10, 0)
        }
    }
}
