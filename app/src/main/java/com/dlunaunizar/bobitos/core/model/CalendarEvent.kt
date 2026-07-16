package com.dlunaunizar.bobitos.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String?,
    val allDay: Boolean,
    val startAt: Instant,
    val endAt: Instant,
    val startDate: LocalDate?,
    val endDateExclusive: LocalDate?,
    val timeZone: String,
    val color: EventColor,
    val participantIds: List<String>,
    val participantNames: List<String>,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
) {
    fun overlaps(start: Instant, endExclusive: Instant): Boolean =
        startAt < endExclusive && endAt > start

    fun displayStartDate(zoneId: ZoneId): LocalDate =
        if (allDay) requireNotNull(startDate) else startAt.atZone(zoneId).toLocalDate()
}

enum class EventColor { BLUE, GREEN, ORANGE, PURPLE, RED, TEAL }
