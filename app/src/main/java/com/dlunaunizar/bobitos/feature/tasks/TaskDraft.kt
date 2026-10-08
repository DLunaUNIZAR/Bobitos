package com.dlunaunizar.bobitos.feature.tasks

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.model.RecurrenceUnit
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskRecurrence
import com.dlunaunizar.bobitos.core.model.TaskType
import kotlinx.parcelize.Parcelize

// Borrador del editor de tarea. Parcelable para que sobreviva a una rotación. Las fechas son el texto
// del campo (se validan al guardar).
@Parcelize
internal data class TaskDraft(
    val title: String,
    val description: String,
    val assigneeId: String?,
    val startDate: String,
    val dueDate: String,
    val priority: TaskPriority,
    val type: TaskType?,
    val recurrenceUnit: RecurrenceUnit?,
    val recurrenceInterval: Int,
) : Parcelable {
    val recurrence: TaskRecurrence? get() = recurrenceUnit?.let { TaskRecurrence(it, recurrenceInterval) }

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
                priority = task?.priority ?: TaskPriority.MEDIUM,
                type = task?.type ?: template?.type,
                recurrenceUnit = recurrence?.unit,
                recurrenceInterval = recurrence?.interval ?: 1,
            )
        }
    }
}
