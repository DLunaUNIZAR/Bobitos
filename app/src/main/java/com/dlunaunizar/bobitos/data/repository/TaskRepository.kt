package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskRecurrence
import com.dlunaunizar.bobitos.core.model.TaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** Cuántas completadas se leen por página (la primera es la que usan Hoy, el Calendario y los recordatorios). */
const val TASKS_COMPLETED_PAGE = 100

/** Tope de seguridad de las pendientes: se leen todas hasta aquí y, si hay más, la pantalla lo avisa. */
const val TASKS_PENDING_CAP = 1000

interface TaskRepository {
    /** Todas las pendientes y las [TASKS_COMPLETED_PAGE] completadas más recientes, de la más nueva a la más vieja. */
    fun tasks(spaceId: String): Flow<List<TaskItem>>

    /**
     * Como [tasks], pero con hasta [completedLimit] completadas y las banderas de «hay más completadas» y «se ha
     * llegado al tope de pendientes», para la pantalla de Tareas.
     */
    fun taskPage(spaceId: String, completedLimit: Int): Flow<PendingAndRecent<TaskItem>> =
        tasks(spaceId).map { PendingAndRecent(it) }

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

    /**
     * Completa o reabre; devuelve las fechas que tiene la tarea después de escribir (adelantadas si se repite), o null
     * si no escribió nada porque una tarea no recurrente ya estaba en el estado pedido (otro la cambió antes).
     */
    suspend fun setCompleted(spaceId: String, taskId: String, completed: Boolean): TaskDates?

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
