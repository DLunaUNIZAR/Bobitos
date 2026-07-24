package com.dlunaunizar.bobitos.feature.tasks

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskStatus
import com.dlunaunizar.bobitos.core.model.TaskType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TasksUiState(
    val tasks: UiState<List<TaskItem>> = UiState.Loading,
    val members: UiState<List<SpaceMember>> = UiState.Loading,
    val filters: TaskFilters = TaskFilters(),
    val isSaving: Boolean = false,
    val error: TaskUiMessage? = null,
    val notice: TaskUiMessage? = null,
)

data class TaskFilters(
    val status: TaskStatus? = TaskStatus.TODO,
    val priority: TaskPriority? = null,
    val type: TaskType? = null,
    val assigneeId: String? = null,
    val unassignedOnly: Boolean = false,
    val date: TaskDateFilter = TaskDateFilter.ALL,
) {
    fun apply(
        tasks: List<TaskItem>,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<TaskItem> {
        val today = now.atZone(zoneId).toLocalDate()
        return tasks.filter { task ->
            (status == null || task.status == status) &&
                (priority == null || task.priority == priority) &&
                (type == null || task.type == type) &&
                (!unassignedOnly || task.assigneeId == null) &&
                (assigneeId == null || task.assigneeId == assigneeId) &&
                date.matches(task, today, zoneId)
        }.sortedWith(
            compareBy<TaskItem> { it.status == TaskStatus.DONE }
                .thenBy { it.dueAt == null }
                .thenBy { it.dueAt }
                .thenByDescending { it.priority }
                .thenByDescending { it.createdAt },
        )
    }
}

enum class TaskDateFilter {
    ALL,
    OVERDUE,
    TODAY,
    UPCOMING,
    NO_DATE,
    ;

    fun matches(task: TaskItem, today: LocalDate, zoneId: ZoneId): Boolean {
        val dueDate = task.dueAt?.atZone(zoneId)?.toLocalDate()
        return when (this) {
            ALL -> true
            OVERDUE -> task.status == TaskStatus.TODO && dueDate?.isBefore(today) == true
            TODAY -> dueDate == today
            UPCOMING -> dueDate?.isAfter(today) == true
            NO_DATE -> dueDate == null
        }
    }
}

// Secciones de la lista (en orden de aparición). Las tareas completadas van al final,
// el resto se agrupan por su fecha de vencimiento relativa a hoy.
enum class TaskSection {
    OVERDUE,
    TODAY,
    UPCOMING,
    NO_DATE,
    COMPLETED,
}

// Agrupa una lista YA filtrada y ordenada en secciones, preservando el orden dentro de cada una.
// Solo devuelve las secciones no vacías, en el orden del enum.
fun List<TaskItem>.groupIntoSections(
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<Pair<TaskSection, List<TaskItem>>> {
    val today = now.atZone(zoneId).toLocalDate()
    val grouped = groupBy { task ->
        val dueDate = task.dueAt?.atZone(zoneId)?.toLocalDate()
        when {
            task.status == TaskStatus.DONE -> TaskSection.COMPLETED
            dueDate == null -> TaskSection.NO_DATE
            dueDate.isBefore(today) -> TaskSection.OVERDUE
            dueDate.isEqual(today) -> TaskSection.TODAY
            else -> TaskSection.UPCOMING
        }
    }
    return TaskSection.entries.mapNotNull { section -> grouped[section]?.let { section to it } }
}

enum class TaskUiMessage {
    TitleRequired,
    TitleTooLong,
    DescriptionTooLong,
    InvalidDate,
    InvalidAssignee,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    TaskNotFound,
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    TaskCreated,
    TaskUpdated,
    TaskCompleted,
    TaskReopened,
    TaskDeleted,
}
