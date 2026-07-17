package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface TaskRepository {
    fun tasks(spaceId: String): Flow<List<TaskItem>>

    suspend fun createTask(
        spaceId: String,
        title: String,
        description: String?,
        assigneeId: String,
        dueAt: Instant?,
        priority: TaskPriority,
    )

    suspend fun updateTask(
        spaceId: String,
        taskId: String,
        title: String,
        description: String?,
        assigneeId: String,
        dueAt: Instant?,
        priority: TaskPriority,
    )

    suspend fun setCompleted(spaceId: String, taskId: String, completed: Boolean)

    suspend fun deleteTask(spaceId: String, taskId: String)
}

enum class TaskFailure {
    TitleRequired,
    TitleTooLong,
    DescriptionTooLong,
    AssigneeRequired,
    InvalidAssignee,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    TaskNotFound,
    PermissionDenied,
    Network,
    Unknown,
}

class TaskRepositoryException(val failure: TaskFailure, cause: Throwable? = null) : Exception(cause)
