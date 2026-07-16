package com.dlunaunizar.bobitos.feature.tasks

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

object TaskValidation {
    const val MAX_TITLE = 120
    const val MAX_DESCRIPTION = 1000

    fun validate(title: String, description: String?, assigneeId: String?): TaskUiMessage? = when {
        title.isBlank() -> TaskUiMessage.TitleRequired
        title.trim().length > MAX_TITLE -> TaskUiMessage.TitleTooLong
        description?.trim()?.length?.let { it > MAX_DESCRIPTION } == true -> {
            TaskUiMessage.DescriptionTooLong
        }
        assigneeId.isNullOrBlank() -> TaskUiMessage.AssigneeRequired
        else -> null
    }

    fun parseDueDate(value: String, zoneId: ZoneId = ZoneId.systemDefault()): Instant? {
        if (value.isBlank()) return null
        return try {
            LocalDate.parse(value.trim()).atStartOfDay(zoneId).toInstant()
        } catch (_: DateTimeParseException) {
            throw InvalidTaskDateException()
        }
    }
}

class InvalidTaskDateException : Exception()
