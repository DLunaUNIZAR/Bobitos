package com.dlunaunizar.bobitos.feature.tasks

import androidx.compose.runtime.saveable.listSaver
import com.dlunaunizar.bobitos.core.model.RecurrenceUnit
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskRecurrence
import com.dlunaunizar.bobitos.core.model.TaskType

// Borrador del editor de tarea: solo tipos que caben en un Bundle (texto, números y nombres de enum),
// para que sobreviva a una rotación. `priorityName`/`typeName`/`recurrenceUnit` son `name` de los enums.
internal data class TaskDraft(
    val title: String,
    val description: String,
    val assigneeId: String?,
    val startDate: String,
    val dueDate: String,
    val priorityName: String,
    val typeName: String?,
    val recurrenceUnit: String?,
    val recurrenceInterval: Int,
) {
    val priority: TaskPriority get() = TaskPriority.valueOf(priorityName)
    val type: TaskType? get() = typeName?.let(TaskType::valueOf)
    val recurrence: TaskRecurrence?
        get() = recurrenceUnit?.let { TaskRecurrence(RecurrenceUnit.valueOf(it), recurrenceInterval) }

    companion object {
        // Por defecto «sin responsable» en tareas nuevas; al editar se conserva el actual.
        fun of(task: TaskItem?, template: TaskTemplate?, templateTitle: String): TaskDraft {
            val recurrence = task?.recurrence ?: template?.recurrence
            return TaskDraft(
                title = task?.title ?: templateTitle,
                description = task?.description.orEmpty(),
                assigneeId = task?.assigneeId,
                startDate = task?.startAt?.formatIsoDate().orEmpty(),
                dueDate = task?.dueAt?.formatIsoDate().orEmpty(),
                priorityName = (task?.priority ?: TaskPriority.MEDIUM).name,
                typeName = (task?.type ?: template?.type)?.name,
                recurrenceUnit = recurrence?.unit?.name,
                recurrenceInterval = recurrence?.interval ?: 1,
            )
        }
    }
}

internal val TaskDraftSaver = listSaver<TaskDraft, Any?>(
    save = {
        listOf(
            it.title, it.description, it.assigneeId, it.startDate, it.dueDate,
            it.priorityName, it.typeName, it.recurrenceUnit, it.recurrenceInterval,
        )
    },
    restore = {
        TaskDraft(
            title = it[0] as String,
            description = it[1] as String,
            assigneeId = it[2] as String?,
            startDate = it[3] as String,
            dueDate = it[4] as String,
            priorityName = it[5] as String,
            typeName = it[6] as String?,
            recurrenceUnit = it[7] as String?,
            recurrenceInterval = it[8] as Int,
        )
    },
)
