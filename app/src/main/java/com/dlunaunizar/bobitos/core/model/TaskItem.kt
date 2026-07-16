package com.dlunaunizar.bobitos.core.model

import java.time.Instant

data class TaskItem(
    val id: String,
    val title: String,
    val description: String?,
    val assigneeId: String?,
    val assigneeName: String?,
    val dueAt: Instant?,
    val priority: TaskPriority,
    val status: TaskStatus,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
    val completedBy: String?,
    val completedByName: String?,
    val completedAt: Instant?,
)

enum class TaskPriority { LOW, MEDIUM, HIGH }

enum class TaskStatus { TODO, DONE }
