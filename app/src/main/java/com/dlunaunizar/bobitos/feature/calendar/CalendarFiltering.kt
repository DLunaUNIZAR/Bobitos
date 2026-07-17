package com.dlunaunizar.bobitos.feature.calendar

import com.dlunaunizar.bobitos.core.model.CalendarEvent

fun List<CalendarEvent>.forSelectedMembers(selectedMemberIds: Set<String>): List<CalendarEvent> =
    filter { event ->
        event.participantIds.isEmpty() || event.participantIds.any(selectedMemberIds::contains)
    }

fun List<CalendarEvent>.forParticipant(userId: String): List<CalendarEvent> =
    filter { event -> userId in event.participantIds }
