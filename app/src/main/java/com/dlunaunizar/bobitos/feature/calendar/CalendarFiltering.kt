package com.dlunaunizar.bobitos.feature.calendar

import com.dlunaunizar.bobitos.core.model.CalendarEvent

/** Sin nadie elegido el filtro dice «Todas» (R4), así que se muestra todo. */
fun List<CalendarEvent>.forSelectedMembers(selectedMemberIds: Set<String>): List<CalendarEvent> =
    if (selectedMemberIds.isEmpty()) {
        this
    } else {
        filter { event -> event.participantIds.isEmpty() || event.participantIds.any(selectedMemberIds::contains) }
    }

fun List<CalendarEvent>.forParticipant(userId: String): List<CalendarEvent> =
    filter { event -> userId in event.participantIds }

/** Sin ningún espacio elegido el filtro dice «Todos» (R4), así que se muestra todo. */
fun List<PersonalCalendarEvent>.forSelectedSpaces(selectedSpaceIds: Set<String>): List<PersonalCalendarEvent> =
    if (selectedSpaceIds.isEmpty()) this else filter { it.spaceId in selectedSpaceIds }
