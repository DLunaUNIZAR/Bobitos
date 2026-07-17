package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

data class EventInput(
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
)

interface CalendarRepository {
    fun events(spaceId: String, rangeStart: Instant, rangeEndExclusive: Instant): Flow<List<CalendarEvent>>
    suspend fun createEvent(spaceId: String, input: EventInput)
    suspend fun updateEvent(spaceId: String, eventId: String, input: EventInput)
    suspend fun deleteEvent(spaceId: String, eventId: String)
}

enum class CalendarFailure {
    TitleRequired,
    TitleTooLong,
    DescriptionTooLong,
    InvalidRange,
    InvalidTimeZone,
    InvalidParticipants,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    EventNotFound,
    PermissionDenied,
    Network,
    Unknown,
}

class CalendarRepositoryException(val failure: CalendarFailure, cause: Throwable? = null) : Exception(cause)
