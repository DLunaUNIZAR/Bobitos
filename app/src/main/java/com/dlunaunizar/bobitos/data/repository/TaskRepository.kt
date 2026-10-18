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

    suspend fun setCompleted(spaceId: String, taskId: String, completed: Boolean)

    /**
     * «Deshacer» completar: deja [previous] (la tarea tal como estaba antes, siempre en TODO) otra vez pendiente con
     * sus fechas de entonces, en una sola escritura. Vale para una normal, que quedó en DONE, y para una recurrente,
     * que siguió en TODO con las fechas adelantadas. Si la tarea ya no existe, lanza [TaskFailure.TaskNotFound]; si otro la cambió desde
     * entonces (ver [completionUndoFailure]), [TaskFailure.TaskChanged] y no escribe nada.
     */
    suspend fun restoreCompletion(spaceId: String, previous: TaskItem)

    suspend fun deleteTask(spaceId: String, taskId: String)

    /**
     * «Deshacer» un borrado: devuelve [task] con su id y su estado. Las reglas obligan a dar de alta una tarea
     * pendiente, así que una completada se escribe después con una actualización directa del estado (nunca por
     * [setCompleted], que en una tarea recurrente avanzaría las fechas). Autoría y fecha de creación son las de quien
     * deshace.
     */
    suspend fun restoreTask(spaceId: String, task: TaskItem)
}

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
