package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskStatus

/** Cómo deja la tarea completarla: hecha, o pendiente con las fechas movidas si se repite. */
fun TaskItem.completedWith(dates: TaskDates): TaskItem = copy(
    status = if (recurrence == null) TaskStatus.DONE else TaskStatus.TODO,
    startAt = dates.startAt,
    dueAt = dates.dueAt,
)

/**
 * Comprueba si «Deshacer» completar sigue siendo seguro: [current] es la tarea tal como está ahora y [completed], como
 * la dejó el completado de [userId] (ver [completedWith]). Devuelve null si sigue igual, y [TaskFailure.TaskChanged]
 * si otro miembro (o él mismo) la tocó después: `updatedBy`, el estado, el título, la descripción, el responsable, la
 * prioridad, el tipo, la repetición o las fechas. Las fechas se comparan en milisegundos, que es como se guardan.
 */
internal fun completionUndoFailure(current: TaskItem, completed: TaskItem, userId: String): TaskFailure? {
    val untouched = current.updatedBy == userId &&
        current.status == completed.status &&
        current.title == completed.title &&
        current.description == completed.description &&
        current.assigneeId == completed.assigneeId &&
        current.priority == completed.priority &&
        current.type == completed.type &&
        current.recurrence == completed.recurrence &&
        current.startAt?.toEpochMilli() == completed.startAt?.toEpochMilli() &&
        current.dueAt?.toEpochMilli() == completed.dueAt?.toEpochMilli()
    return if (untouched) null else TaskFailure.TaskChanged
}
