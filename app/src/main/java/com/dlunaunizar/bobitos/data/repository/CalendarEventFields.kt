package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.time.Instant
import java.time.LocalDate
import java.util.Date

/**
 * Lee un evento de sus campos; null, sin lanzar nunca, si falta un campo obligatorio o alguno está mal formado
 * (una fecha o un color que no se pueden leer). `updatedAt` cae a `createdAt`. Lo comparten Calendario y Deporte.
 */
internal fun calendarEventOf(id: String, data: Map<String, Any?>): CalendarEvent? = runCatching {
    fun text(key: String) = requireNotNull(data[key] as? String)
    fun instant(key: String) = (data[key] as? Timestamp)?.toDate()?.toInstant()
    fun names(key: String) = (data[key] as? List<*>)?.filterIsInstance<String>().orEmpty()
    val created = requireNotNull(instant("createdAt"))
    CalendarEvent(
        id = id,
        title = text("title"),
        description = data["description"] as? String,
        allDay = requireNotNull(data["allDay"] as? Boolean),
        startAt = requireNotNull(instant("startAt")),
        endAt = requireNotNull(instant("endAt")),
        startDate = (data["startDate"] as? String)?.let(LocalDate::parse),
        endDateExclusive = (data["endDateExclusive"] as? String)?.let(LocalDate::parse),
        timeZone = text("timeZone"),
        color = EventColor.valueOf(text("color")),
        participantIds = names("participantIds"),
        participantNames = names("participantNames"),
        createdBy = text("createdBy"),
        createdByName = text("createdByName"),
        createdAt = created,
        updatedBy = text("updatedBy"),
        updatedAt = instant("updatedAt") ?: created,
    )
}.getOrNull()

internal fun DocumentSnapshot.toCalendarEvent(): CalendarEvent? = data?.let { calendarEventOf(id, it) }

private fun EventInput.common(names: List<String>) = mapOf(
    "title" to title, "description" to description, "allDay" to allDay,
    "startAt" to startAt.timestamp(), "endAt" to endAt.timestamp(),
    "startDate" to startDate?.toString(), "endDateExclusive" to endDateExclusive?.toString(),
    "timeZone" to timeZone, "color" to color.name, "participantIds" to participantIds,
    "participantNames" to names,
)

internal fun EventInput.data(user: AuthUser, names: List<String>): Map<String, Any?> = common(names) + mapOf(
    "createdBy" to user.id,
    "createdByName" to user.displayName.ifBlank { user.email.substringBefore('@') }.take(60),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedBy" to user.id,
    "updatedAt" to FieldValue.serverTimestamp(),
)

internal fun EventInput.updateData(user: AuthUser, names: List<String>): Map<String, Any?> = common(names) + mapOf(
    "updatedBy" to user.id,
    "updatedAt" to FieldValue.serverTimestamp(),
)

/** Reconstruye el input desde un evento existente, para restaurarlo al «Deshacer» un borrado. */
fun CalendarEvent.toInput(): EventInput = EventInput(
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

internal fun Instant.timestamp() = Timestamp(Date.from(this))
