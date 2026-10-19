package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskRecurrence
import com.dlunaunizar.bobitos.core.model.TaskType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface TaskRepository {
    fun tasks(spaceId: String): Flow<List<TaskItem>>

    suspend fun createTask(
        spaceId: String,
        title: String,
        description: String?,
        assigneeId: String?,
        dueAt: Instant?,
        priority: TaskPriority,
        type: TaskType?,
        recurrence: TaskRecurrence?,
        startAt: Instant?,
    ): String

    suspend fun updateTask(
        spaceId: String,
        taskId: String,
        title: String,
        description: String?,
        assigneeId: String?,
        dueAt: Instant?,
        priority: TaskPriority,
        type: TaskType?,
        recurrence: TaskRecurrence?,
        startAt: Instant?,
    )

    /** Completa o reabre; devuelve las fechas que tiene la tarea después de escribir (adelantadas si se repite). */
    suspend fun setCompleted(spaceId: String, taskId: String, completed: Boolean): TaskDates

    /**
     * «Deshacer» completar: deja [previous] (la tarea tal como estaba antes, siempre en TODO) otra vez pendiente con
     * sus fechas de entonces, en una sola escritura. Vale para una normal, que quedó en DONE, y para una recurrente,
     * que siguió en TODO con las fechas adelantadas. Solo si la tarea sigue como la dejó [completed] (ver
     * [completionUndoFailure]); si otro la cambió desde entonces, lanza [TaskFailure.TaskChanged] sin escribir. Si ya
     * no existe, [TaskFailure.TaskNotFound].
     */
    suspend fun restoreCompletion(spaceId: String, previous: TaskItem, completed: TaskItem)

    suspend fun deleteTask(spaceId: String, taskId: String)

    /**
     * «Deshacer» un alta: borra la tarea [taskId] solo si quien deshace es el último que la escribió. Lanza
     * [TaskFailure.TaskNotFound] si ya no existe y [TaskFailure.TaskChanged] si otro la tocó, sin borrar.
     */
    suspend fun undoCreateTask(spaceId: String, taskId: String)

    /**
     * «Deshacer» un borrado: devuelve [task] con su id y su estado, en una sola escritura por el mismo camino que
     * [createTask]. Si estaba completada vuelve hecha por quien deshace. Si su responsable ya no es miembro activo,
     * vuelve sin responsable en lugar de fallar. Autoría y fecha de creación son las de quien deshace.
     */
    suspend fun restoreTask(spaceId: String, task: TaskItem)
}

/** Las fechas que deja una escritura de completar, en milisegundos (como se guardan). */
data class TaskDates(val startAt: Instant?, val dueAt: Instant?)

enum class TaskFailure {
    TitleRequired,
    TitleTooLong,
    DescriptionTooLong,
    InvalidAssignee,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    TaskNotFound,
    TaskChanged,
    PermissionDenied,
    Network,
    Unknown,
}

class TaskRepositoryException(val failure: TaskFailure, cause: Throwable? = null) : Exception(cause)
