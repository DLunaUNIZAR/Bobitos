package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskStatus
import com.dlunaunizar.bobitos.data.sync.RealtimeMetrics
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import com.dlunaunizar.bobitos.data.sync.WriteNotAllowedException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
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
        assigneeId: String,
        dueAt: Instant?,
        priority: TaskPriority,
    ) = runTaskOperation {
        val user = requireVerifiedUser()
        val values = validate(title, description, assigneeId)
        val spaceReference = firestore.collection(SPACES).document(spaceId)
        val assigneeReference = membershipReference(spaceId, assigneeId)
        val taskReference = tasksCollection(spaceId).document()
        firestore.runTransaction { transaction ->
            if (!transaction.get(spaceReference).exists()) {
                throw TaskRepositoryException(TaskFailure.SpaceNotFound)
            }
            val assignee = transaction.get(assigneeReference)
            requireActiveAssignee(assignee, assigneeId)
            transaction.set(
                taskReference,
                taskData(
                    values = values,
                    assigneeName = assignee.getString(FIELD_DISPLAY_NAME).orEmpty(),
                    dueAt = dueAt,
                    priority = priority,
                    user = user,
                ),
            )
        }.await()
        Unit
    }

    override suspend fun updateTask(
        spaceId: String,
        taskId: String,
        title: String,
        description: String?,
        assigneeId: String,
        dueAt: Instant?,
        priority: TaskPriority,
    ) = runTaskOperation {
        val user = requireVerifiedUser()
        val values = validate(title, description, assigneeId)
        val taskReference = tasksCollection(spaceId).document(taskId)
        val assigneeReference = membershipReference(spaceId, assigneeId)
        firestore.runTransaction { transaction ->
            requireTask(transaction.get(taskReference))
            val assignee = transaction.get(assigneeReference)
            requireActiveAssignee(assignee, assigneeId)
            transaction.update(
                taskReference,
                FIELD_TITLE, values.title,
                FIELD_DESCRIPTION, values.description,
                FIELD_ASSIGNEE_ID, assigneeId,
                FIELD_ASSIGNEE_NAME, assignee.getString(FIELD_DISPLAY_NAME),
                FIELD_DUE_AT, dueAt?.toTimestamp(),
                FIELD_PRIORITY, priority.name,
                FIELD_UPDATED_BY, user.id,
                FIELD_UPDATED_AT, FieldValue.serverTimestamp(),
            )
        }.await()
        Unit
    }

    override suspend fun setCompleted(spaceId: String, taskId: String, completed: Boolean) = runTaskOperation {
        val user = requireVerifiedUser()
        val taskReference = tasksCollection(spaceId).document(taskId)
        firestore.runTransaction { transaction ->
            val task = requireTask(transaction.get(taskReference))
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

    private fun tasksCollection(spaceId: String) = firestore.collection(SPACES)
        .document(spaceId).collection(TASKS)

    private fun membershipReference(spaceId: String, userId: String) = firestore
        .collection(MEMBERSHIPS).document("${spaceId}_$userId")

    private fun taskData(
        values: TaskValues,
        assigneeName: String,
        dueAt: Instant?,
        priority: TaskPriority,
        user: AuthUser,
    ) = mapOf(
        FIELD_TITLE to values.title,
        FIELD_DESCRIPTION to values.description,
        FIELD_ASSIGNEE_ID to values.assigneeId,
        FIELD_ASSIGNEE_NAME to assigneeName,
        FIELD_DUE_AT to dueAt?.toTimestamp(),
        FIELD_PRIORITY to priority.name,
        FIELD_STATUS to STATUS_TODO,
        FIELD_CREATED_BY to user.id,
        FIELD_CREATED_BY_NAME to user.taskDisplayName,
        FIELD_CREATED_AT to FieldValue.serverTimestamp(),
        FIELD_UPDATED_BY to user.id,
        FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
        FIELD_COMPLETED_BY to null,
        FIELD_COMPLETED_BY_NAME to null,
        FIELD_COMPLETED_AT to null,
    )

    private fun validate(title: String, description: String?, assigneeId: String): TaskValues {
        val normalizedTitle = title.trim()
        val normalizedDescription = description?.trim()?.takeIf(String::isNotEmpty)
        when {
            normalizedTitle.isEmpty() -> throw TaskRepositoryException(TaskFailure.TitleRequired)
            normalizedTitle.length > MAX_TITLE_LENGTH -> throw TaskRepositoryException(TaskFailure.TitleTooLong)
            normalizedDescription != null && normalizedDescription.length > MAX_DESCRIPTION_LENGTH -> {
                throw TaskRepositoryException(TaskFailure.DescriptionTooLong)
            }
            assigneeId.isBlank() -> throw TaskRepositoryException(TaskFailure.AssigneeRequired)
        }
        return TaskValues(normalizedTitle, normalizedDescription, assigneeId)
    }

    private fun requireVerifiedUser(): AuthUser {
        val user = authRepository.currentUser.value
            ?: throw TaskRepositoryException(TaskFailure.NotAuthenticated)
        if (!user.isEmailVerified) throw TaskRepositoryException(TaskFailure.EmailNotVerified)
        return user
    }

    private fun requireActiveAssignee(snapshot: DocumentSnapshot, assigneeId: String) {
        if (!snapshot.exists() ||
            snapshot.getString(FIELD_USER_ID) != assigneeId ||
            snapshot.getString(FIELD_MEMBERSHIP_STATUS) != MEMBERSHIP_ACTIVE
        ) {
            throw TaskRepositoryException(TaskFailure.InvalidAssignee)
        }
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

    private data class TaskValues(val title: String, val description: String?, val assigneeId: String)

    private companion object {
        const val SPACES = "spaces"
        const val TASKS = "tasks"
        const val MEMBERSHIPS = "memberships"
        const val FIELD_TITLE = "title"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_ASSIGNEE_ID = "assigneeId"
        const val FIELD_ASSIGNEE_NAME = "assigneeName"
        const val FIELD_DUE_AT = "dueAt"
        const val FIELD_PRIORITY = "priority"
        const val FIELD_STATUS = "status"
        const val FIELD_CREATED_BY = "createdBy"
        const val FIELD_CREATED_BY_NAME = "createdByName"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_BY = "updatedBy"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_COMPLETED_BY = "completedBy"
        const val FIELD_COMPLETED_BY_NAME = "completedByName"
        const val FIELD_COMPLETED_AT = "completedAt"
        const val FIELD_DISPLAY_NAME = "displayName"
        const val FIELD_USER_ID = "userId"
        const val FIELD_MEMBERSHIP_STATUS = "status"
        const val MEMBERSHIP_ACTIVE = "ACTIVE"
        const val STATUS_TODO = "TODO"
        const val STATUS_DONE = "DONE"
        const val MAX_TITLE_LENGTH = 120
        const val MAX_DESCRIPTION_LENGTH = 1000
        const val MAX_VISIBLE_TASKS = 250L
    }
}

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
    )
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
