package com.dlunaunizar.bobitos.feature.calendar

import androidx.compose.runtime.saveable.listSaver
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// Borrador del editor de evento: solo tipos que caben en un Bundle (texto, booleanos y listas de
// texto), para que sobreviva a una rotación. Fechas en ISO, horas en HH:mm y el color por su `name`.
internal data class EventDraft(
    val title: String,
    val description: String,
    val allDay: Boolean,
    val startDateIso: String,
    val endDateIso: String,
    val startTimeText: String,
    val endTimeText: String,
    val colorName: String,
    val selectedIds: List<String>,
) {
    val startDate: LocalDate get() = LocalDate.parse(startDateIso)
    val endDate: LocalDate get() = LocalDate.parse(endDateIso)
    val startTime: LocalTime get() = LocalTime.parse(startTimeText)
    val endTime: LocalTime get() = LocalTime.parse(endTimeText)
    val color: EventColor get() = EventColor.valueOf(colorName)

    fun withStartDate(date: LocalDate) = copy(startDateIso = date.toString())
    fun withEndDate(date: LocalDate) = copy(endDateIso = date.toString())
    fun withStartTime(time: LocalTime) = copy(startTimeText = time.toString())
    fun withEndTime(time: LocalTime) = copy(endTimeText = time.toString())
    fun withColor(value: EventColor) = copy(colorName = value.name)
    fun withParticipant(userId: String, selected: Boolean) = copy(
        selectedIds = if (selected) (selectedIds + userId).distinct() else selectedIds - userId,
    )

    companion object {
        /** Valores iniciales: los del evento al editar; al crear, el día enfocado y, si viene de una hora, 1 h de duración. */
        fun of(event: CalendarEvent?, day: LocalDate, initialStart: LocalTime?, zone: ZoneId) = EventDraft(
            title = event?.title.orEmpty(),
            description = event?.description.orEmpty(),
            allDay = event?.allDay ?: (initialStart == null),
            startDateIso = initialStartDate(event, day, zone).toString(),
            endDateIso = initialEndDate(event, day, zone).toString(),
            startTimeText = initialStartTime(event, initialStart, zone).toString(),
            endTimeText = initialEndTime(event, initialStart, zone).toString(),
            colorName = (event?.color ?: EventColor.BLUE).name,
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

internal val EventDraftSaver = listSaver<EventDraft, Any?>(
    save = {
        listOf(
            it.title, it.description, it.allDay, it.startDateIso, it.endDateIso,
            it.startTimeText, it.endTimeText, it.colorName, ArrayList(it.selectedIds),
        )
    },
    restore = {
        @Suppress("UNCHECKED_CAST")
        EventDraft(
            title = it[0] as String,
            description = it[1] as String,
            allDay = it[2] as Boolean,
            startDateIso = it[3] as String,
            endDateIso = it[4] as String,
            startTimeText = it[5] as String,
            endTimeText = it[6] as String,
            colorName = it[7] as String,
            selectedIds = (it[8] as List<String>).toList(),
        )
    },
)
