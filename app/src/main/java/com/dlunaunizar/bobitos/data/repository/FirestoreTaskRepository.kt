package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.RecurrenceUnit
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskRecurrence
import com.dlunaunizar.bobitos.core.model.TaskStatus
import com.dlunaunizar.bobitos.core.model.TaskType
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreTaskRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val realtimeMetrics: RealtimeMetrics,
) : TaskRepository {
    private val firestore = FirebaseFirestore.getInstance()

    override fun tasks(spaceId: String): Flow<List<TaskItem>> = callbackFlow {
        val metricId = realtimeMetrics.listenerStarted("tasks:active")
        val registration = tasksCollection(spaceId)
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .limit(MAX_VISIBLE_TASKS)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> close(error.toTaskRepositoryException())
                    snapshot != null -> {
                        realtimeMetrics.snapshotReceived(
                            scope = "tasks:active",
                            changedDocuments = snapshot.documentChanges.size,
                            fromCache = snapshot.metadata.isFromCache,
                        )
                        trySend(snapshot.documents.mapNotNull(DocumentSnapshot::toTaskItem))
                    }
                }
            }
        awaitClose {
            registration.remove()
            realtimeMetrics.listenerStopped(metricId)
        }
    }

    override suspend fun createTask(
        spaceId: String,
        title: String,
        description: String?,
        assigneeId: String?,
        dueAt: Instant?,
        priority: TaskPriority,
        type: TaskType?,
        recurrence: TaskRecurrence?,
        startAt: Instant?,
    ): String = runTaskOperation {
        writeNewTask(
            spaceId = spaceId,
            taskReference = tasksCollection(spaceId).document(),
            fields = NewTask(title, description, assigneeId, dueAt, priority, type, recurrence, startAt),
            completed = false,
            restoring = false,
        )
    }

    override suspend fun updateTask(
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
    ) = runTaskOperation {
        val user = requireVerifiedUser()
        val values = validate(title, description, assigneeId)
        val taskReference = tasksCollection(spaceId).document(taskId)
        firestore.runTransaction { transaction ->
            requireTask(transaction.get(taskReference))
            val assigneeName = resolveAssigneeName(transaction, spaceId, values.assigneeId)
            transaction.update(
                taskReference,
                FIELD_TITLE, values.title,
                FIELD_DESCRIPTION, values.description,
                FIELD_ASSIGNEE_ID, values.assigneeId,
                FIELD_ASSIGNEE_NAME, assigneeName,
                FIELD_DUE_AT, dueAt?.toTimestamp(),
                FIELD_START_AT, startAt?.toTimestamp(),
                FIELD_PRIORITY, priority.name,
                FIELD_TYPE, type?.name,
                FIELD_RECURRENCE_UNIT, recurrence?.unit?.name,
                FIELD_RECURRENCE_INTERVAL, recurrence?.interval,
                FIELD_UPDATED_BY, user.id,
                FIELD_UPDATED_AT, FieldValue.serverTimestamp(),
            )
        }.await()
        Unit
    }

    override suspend fun setCompleted(spaceId: String, taskId: String, completed: Boolean): TaskDates =
        runTaskOperation {
            val user = requireVerifiedUser()
            val taskReference = tasksCollection(spaceId).document(taskId)
            firestore.runTransaction { transaction ->
                val task = requireTask(transaction.get(taskReference))
                val recurrence = task.taskRecurrence()
                val oldStart = task.getTimestamp(FIELD_START_AT)?.toDate()?.toInstant()
                val oldDue = task.getTimestamp(FIELD_DUE_AT)?.toDate()?.toInstant()
                if (completed && recurrence != null) {
                    // Tarea recurrente: al marcarla hecha se reprograma a la siguiente
                    // fecha (avanza inicio y fin) en vez de completarse (sigue pendiente).
                    val newStart = oldStart?.let { advanceRecurrence(it, recurrence) }?.truncatedTo(ChronoUnit.MILLIS)
                    val newDue = when {
                        oldDue != null -> advanceRecurrence(oldDue, recurrence)
                        oldStart == null -> advanceRecurrence(Instant.now(), recurrence)
                        else -> null
                    }?.truncatedTo(ChronoUnit.MILLIS)
                    transaction.update(
                        taskReference,
                        FIELD_START_AT,
                        newStart?.toTimestamp(),
                        FIELD_DUE_AT,
                        newDue?.toTimestamp(),
                        FIELD_UPDATED_BY,
                        user.id,
                        FIELD_UPDATED_AT,
                        FieldValue.serverTimestamp(),
                    )
                    TaskDates(newStart, newDue)
                } else {
                    val targetStatus = if (completed) STATUS_DONE else STATUS_TODO
                    if (task.getString(FIELD_STATUS) != targetStatus) {
                        transaction.update(
                            taskReference,
                            FIELD_STATUS, targetStatus,
                            FIELD_COMPLETED_BY, if (completed) user.id else null,
                            FIELD_COMPLETED_BY_NAME, if (completed) user.taskDisplayName else null,
                            FIELD_COMPLETED_AT,
                            if (completed) FieldValue.serverTimestamp() else null,
                            FIELD_UPDATED_BY, user.id,
                            FIELD_UPDATED_AT, FieldValue.serverTimestamp(),
                        )
                    }
                    TaskDates(oldStart, oldDue)
                }
            }.await()
        }

    override suspend fun restoreCompletion(spaceId: String, previous: TaskItem, completed: TaskItem) =
        runTaskOperation {
            require(previous.status == TaskStatus.TODO) { "La foto de antes de completar debe estar pendiente" }
            val user = requireVerifiedUser()
            val taskReference = tasksCollection(spaceId).document(previous.id)
            firestore.runTransaction { transaction ->
                val current = requireTask(transaction.get(taskReference)).toTaskItem()
                if (current != null) {
                    completionUndoFailure(current, completed, user.id)?.let { throw TaskRepositoryException(it) }
                }
                transaction.update(
                    taskReference,
                    FIELD_STATUS, STATUS_TODO,
                    FIELD_COMPLETED_BY, null,
                    FIELD_COMPLETED_BY_NAME, null,
                    FIELD_COMPLETED_AT, null,
                    FIELD_START_AT, previous.startAt?.toTimestamp(),
                    FIELD_DUE_AT, previous.dueAt?.toTimestamp(),
                    FIELD_UPDATED_BY, user.id,
                    FIELD_UPDATED_AT, FieldValue.serverTimestamp(),
                )
            }.await()
            Unit
        }

    override suspend fun deleteTask(spaceId: String, taskId: String) = runTaskOperation {
        requireVerifiedUser()
        val taskReference = tasksCollection(spaceId).document(taskId)
        firestore.runTransaction { transaction ->
            requireTask(transaction.get(taskReference))
            transaction.delete(taskReference)
        }.await()
        Unit
    }

    override suspend fun undoCreateTask(spaceId: String, taskId: String) = runTaskOperation {
        val user = requireVerifiedUser()
        val taskReference = tasksCollection(spaceId).document(taskId)
        firestore.runTransaction { transaction ->
            val task = requireTask(transaction.get(taskReference))
            if (!isLastWriter(task.getString(FIELD_UPDATED_BY), user.id)) {
                throw TaskRepositoryException(TaskFailure.TaskChanged)
            }
            transaction.delete(taskReference)
        }.await()
        Unit
    }

    override suspend fun restoreTask(spaceId: String, task: TaskItem) = runTaskOperation {
        writeNewTask(
            spaceId = spaceId,
            taskReference = tasksCollection(spaceId).document(task.id),
            fields = NewTask(
                task.title,
                task.description,
                task.assigneeId,
                task.dueAt,
                task.priority,
                task.type,
                task.recurrence,
                task.startAt,
            ),
            completed = task.status == TaskStatus.DONE,
            restoring = true,
        )
        Unit
    }

    // El alta y la restauración pasan por aquí: misma validación, misma transacción y los mismos campos. Al
    // restaurar, un responsable que ya no es miembro activo se quita en lugar de fallar.
    private suspend fun writeNewTask(
        spaceId: String,
        taskReference: DocumentReference,
        fields: NewTask,
        completed: Boolean,
        restoring: Boolean,
    ): String {
        val user = requireVerifiedUser()
        val values = validate(fields.title, fields.description, fields.assigneeId)
        val spaceReference = firestore.collection(SPACES).document(spaceId)
        firestore.runTransaction { transaction ->
            if (!transaction.get(spaceReference).exists()) {
                throw TaskRepositoryException(TaskFailure.SpaceNotFound)
            }
            val ids = listOfNotNull(values.assigneeId)
            val assignee = activeMembers(
                ids,
                ids.map { transaction.get(membershipReference(spaceId, it)).toMembershipRecord() },
            ).singleOrNull()
            if (assignee == null && values.assigneeId != null && !restoring) {
                throw TaskRepositoryException(TaskFailure.InvalidAssignee)
            }
            transaction.set(
                taskReference,
                taskData(
                    values = values.copy(assigneeId = assignee?.id),
                    assigneeName = assignee?.name,
                    dueAt = fields.dueAt,
                    priority = fields.priority,
                    type = fields.type,
                    recurrence = fields.recurrence,
                    startAt = fields.startAt,
                    user = user,
                    completed = completed,
                ),
            )
        }.await()
        return taskReference.id
    }

    private fun tasksCollection(spaceId: String) = firestore.collection(SPACES)
        .document(spaceId).collection(TASKS)

    private fun membershipReference(spaceId: String, userId: String) = firestore
        .collection(MEMBERSHIPS).document("${spaceId}_$userId")

    private fun validate(title: String, description: String?, assigneeId: String?): TaskValues {
        val normalizedTitle = title.trim()
        val normalizedDescription = description?.trim()?.takeIf(String::isNotEmpty)
        val normalizedAssignee = assigneeId?.trim()?.takeIf(String::isNotEmpty)
        when {
            normalizedTitle.isEmpty() -> throw TaskRepositoryException(TaskFailure.TitleRequired)
            normalizedTitle.length > MAX_TITLE_LENGTH -> throw TaskRepositoryException(TaskFailure.TitleTooLong)
            normalizedDescription != null && normalizedDescription.length > MAX_DESCRIPTION_LENGTH -> {
                throw TaskRepositoryException(TaskFailure.DescriptionTooLong)
            }
        }
        return TaskValues(normalizedTitle, normalizedDescription, normalizedAssignee)
    }

    // Resuelve el nombre del responsable dentro de la transacción: null si la tarea queda sin
    // responsable; si hay uno, exige que sea miembro activo (lee su ficha de membresía).
    private fun resolveAssigneeName(transaction: Transaction, spaceId: String, assigneeId: String?): String? {
        if (assigneeId == null) return null
        val record = transaction.get(membershipReference(spaceId, assigneeId)).toMembershipRecord()
        val assignee = activeMembers(listOf(assigneeId), listOf(record)).singleOrNull()
            ?: throw TaskRepositoryException(TaskFailure.InvalidAssignee)
        return assignee.name
    }

    private fun requireVerifiedUser(): AuthUser {
        val user = authRepository.currentUser.value
            ?: throw TaskRepositoryException(TaskFailure.NotAuthenticated)
        if (!user.isEmailVerified) throw TaskRepositoryException(TaskFailure.EmailNotVerified)
        return user
    }

    private fun requireTask(snapshot: DocumentSnapshot): DocumentSnapshot {
        if (!snapshot.exists()) throw TaskRepositoryException(TaskFailure.TaskNotFound)
        return snapshot
    }

    private suspend inline fun <T> runTaskOperation(crossinline operation: suspend () -> T): T {
        try {
            syncRepository.requireWritable()
            return operation()
        } catch (error: TaskRepositoryException) {
            throw error
        } catch (error: WriteNotAllowedException) {
            throw TaskRepositoryException(TaskFailure.Network, error)
        } catch (error: Throwable) {
            syncRepository.reportWriteFailure(error)
            throw error.toTaskRepositoryException()
        }
    }
}

private const val SPACES = "spaces"
private const val TASKS = "tasks"
private const val MEMBERSHIPS = "memberships"
private const val FIELD_TITLE = "title"
private const val FIELD_DESCRIPTION = "description"
private const val FIELD_ASSIGNEE_ID = "assigneeId"
private const val FIELD_ASSIGNEE_NAME = "assigneeName"
private const val FIELD_DUE_AT = "dueAt"
private const val FIELD_START_AT = "startAt"
private const val FIELD_PRIORITY = "priority"
private const val FIELD_TYPE = "type"
private const val FIELD_RECURRENCE_UNIT = "recurrenceUnit"
private const val FIELD_RECURRENCE_INTERVAL = "recurrenceInterval"
private const val FIELD_STATUS = "status"
private const val FIELD_CREATED_BY = "createdBy"
private const val FIELD_CREATED_BY_NAME = "createdByName"
private const val FIELD_CREATED_AT = "createdAt"
private const val FIELD_UPDATED_BY = "updatedBy"
private const val FIELD_UPDATED_AT = "updatedAt"
private const val FIELD_COMPLETED_BY = "completedBy"
private const val FIELD_COMPLETED_BY_NAME = "completedByName"
private const val FIELD_COMPLETED_AT = "completedAt"
private const val STATUS_TODO = "TODO"
private const val STATUS_DONE = "DONE"
private const val MAX_TITLE_LENGTH = 120
private const val MAX_DESCRIPTION_LENGTH = 1000
private const val MAX_VISIBLE_TASKS = 250L

private val AuthUser.taskDisplayName: String
    get() = displayName.ifBlank { email.substringBefore('@') }.take(60)

private fun Instant.toTimestamp() = Timestamp(Date.from(this))

private fun DocumentSnapshot.toTaskItem(): TaskItem? {
    val createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: return null
    return TaskItem(
        id = id,
        title = getString("title") ?: return null,
        description = getString("description"),
        assigneeId = getString("assigneeId"),
        assigneeName = getString("assigneeName"),
        dueAt = getTimestamp("dueAt")?.toDate()?.toInstant(),
        priority = runCatching { TaskPriority.valueOf(getString("priority").orEmpty()) }.getOrNull()
            ?: return null,
        status = runCatching { TaskStatus.valueOf(getString("status").orEmpty()) }.getOrNull()
            ?: return null,
        createdBy = getString("createdBy") ?: return null,
        createdByName = getString("createdByName") ?: getString("createdBy") ?: return null,
        createdAt = createdAt,
        updatedBy = getString("updatedBy") ?: getString("createdBy") ?: return null,
        updatedAt = getTimestamp("updatedAt")?.toDate()?.toInstant() ?: createdAt,
        completedBy = getString("completedBy"),
        completedByName = getString("completedByName"),
        completedAt = getTimestamp("completedAt")?.toDate()?.toInstant(),
        type = getString("type")?.let { value ->
            runCatching { TaskType.valueOf(value) }.getOrNull()
        },
        recurrence = taskRecurrence(),
        startAt = getTimestamp("startAt")?.toDate()?.toInstant(),
    )
}

private fun DocumentSnapshot.taskRecurrence(): TaskRecurrence? {
    val unit = getString("recurrenceUnit")
        ?.let { runCatching { RecurrenceUnit.valueOf(it) }.getOrNull() }
        ?: return null
    val interval = (getLong("recurrenceInterval") ?: 1L).toInt().coerceAtLeast(1)
    return TaskRecurrence(unit, interval)
}

private fun advanceRecurrence(base: Instant, recurrence: TaskRecurrence): Instant {
    val zoned = base.atZone(ZoneId.systemDefault())
    val advanced = when (recurrence.unit) {
        RecurrenceUnit.DAY -> zoned.plusDays(recurrence.interval.toLong())
        RecurrenceUnit.WEEK -> zoned.plusWeeks(recurrence.interval.toLong())
        RecurrenceUnit.MONTH -> zoned.plusMonths(recurrence.interval.toLong())
    }
    return advanced.toInstant()
}

private fun Throwable.toTaskRepositoryException() = TaskRepositoryException(
    failure = when ((this as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> TaskFailure.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> TaskFailure.TaskNotFound
        FirebaseFirestoreException.Code.UNAVAILABLE -> TaskFailure.Network
        else -> TaskFailure.Unknown
    },
    cause = this,
)

internal data class TaskValues(val title: String, val description: String?, val assigneeId: String?)

private data class NewTask(
    val title: String,
    val description: String?,
    val assigneeId: String?,
    val dueAt: Instant?,
    val priority: TaskPriority,
    val type: TaskType?,
    val recurrence: TaskRecurrence?,
    val startAt: Instant?,
)

/** Los campos de una tarea nueva. Con [completed], nace hecha por [user] (así vuelve una tarea borrada y completada). */
@Suppress("LongParameterList")
internal fun taskData(
    values: TaskValues,
    assigneeName: String?,
    dueAt: Instant?,
    priority: TaskPriority,
    type: TaskType?,
    recurrence: TaskRecurrence?,
    startAt: Instant?,
    user: AuthUser,
    completed: Boolean,
) = mapOf(
    FIELD_TITLE to values.title,
    FIELD_DESCRIPTION to values.description,
    FIELD_ASSIGNEE_ID to values.assigneeId,
    FIELD_ASSIGNEE_NAME to assigneeName,
    FIELD_DUE_AT to dueAt?.toTimestamp(),
    FIELD_START_AT to startAt?.toTimestamp(),
    FIELD_PRIORITY to priority.name,
    FIELD_TYPE to type?.name,
    FIELD_RECURRENCE_UNIT to recurrence?.unit?.name,
    FIELD_RECURRENCE_INTERVAL to recurrence?.interval,
    FIELD_STATUS to if (completed) STATUS_DONE else STATUS_TODO,
    FIELD_CREATED_BY to user.id,
    FIELD_CREATED_BY_NAME to user.taskDisplayName,
    FIELD_CREATED_AT to FieldValue.serverTimestamp(),
    FIELD_UPDATED_BY to user.id,
    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
    FIELD_COMPLETED_BY to if (completed) user.id else null,
    FIELD_COMPLETED_BY_NAME to if (completed) user.taskDisplayName else null,
    FIELD_COMPLETED_AT to if (completed) FieldValue.serverTimestamp() else null,
)
