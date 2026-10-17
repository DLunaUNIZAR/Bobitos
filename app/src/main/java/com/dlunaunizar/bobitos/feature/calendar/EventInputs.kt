package com.dlunaunizar.bobitos.feature.calendar

import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.data.repository.EventInput

// Reconstruye el input desde un evento existente, para re-crearlo al «Deshacer» un borrado.
internal fun CalendarEvent.toInput(): EventInput = EventInput(
    title = title,
    description = description,
    allDay = allDay,
    startAt = startAt,
    endAt = endAt,
    startDate = startDate,
    endDateExclusive = endDateExclusive,
    timeZone = timeZone,
    color = color,
    participantIds = participantIds,
)
