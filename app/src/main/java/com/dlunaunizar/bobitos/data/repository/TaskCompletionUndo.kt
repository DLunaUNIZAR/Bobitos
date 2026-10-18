package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskStatus

/**
 * Comprueba si «Deshacer» completar sigue siendo seguro: [current] es la tarea tal como está ahora y [previous], la foto
 * de antes de completarla. Devuelve null si la tarea sigue como la dejó el completado de [userId], y
 * [TaskFailure.TaskChanged] si otro miembro la tocó (`updatedBy`), si una normal ya no está hecha, si una que se
 * repite ya no está pendiente o si cambió el título, la descripción, el responsable, la prioridad, el tipo o la
 * repetición.
 */
internal fun completionUndoFailure(current: TaskItem, previous: TaskItem, userId: String): TaskFailure? {
    val expectedStatus = if (previous.recurrence == null) TaskStatus.DONE else TaskStatus.TODO
    val untouched = current.updatedBy == userId &&
        current.status == expectedStatus &&
        current.title == previous.title &&
        current.description == previous.description &&
        current.assigneeId == previous.assigneeId &&
        current.priority == previous.priority &&
        current.type == previous.type &&
        current.recurrence == previous.recurrence
    return if (untouched) null else TaskFailure.TaskChanged
}
