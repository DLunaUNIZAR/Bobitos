package com.dlunaunizar.bobitos.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.TaskFailure
import com.dlunaunizar.bobitos.data.repository.TaskRepository
import com.dlunaunizar.bobitos.data.repository.TaskRepositoryException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val spaceRepository: SpaceRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = mutableUiState.asStateFlow()
    private var tasksJob: Job? = null
    private var membersJob: Job? = null

    fun observe(spaceId: String) {
        stopObserving()
        mutableUiState.update { it.copy(tasks = UiState.Loading, members = UiState.Loading) }
        tasksJob = viewModelScope.launch {
            taskRepository.tasks(spaceId).catch { error ->
                mutableUiState.update { it.copy(tasks = UiState.Error(error.message)) }
            }.collect { tasks ->
                mutableUiState.update { it.copy(tasks = UiState.Content(tasks)) }
            }
        }
        membersJob = viewModelScope.launch {
            spaceRepository.members(spaceId).catch { error ->
                mutableUiState.update { it.copy(members = UiState.Error(error.message)) }
            }.collect { members ->
                mutableUiState.update { it.copy(members = UiState.Content(members)) }
            }
        }
    }

    fun stopObserving() {
        tasksJob?.cancel()
        tasksJob = null
        membersJob?.cancel()
        membersJob = null
    }

    fun setFilters(filters: TaskFilters) = mutableUiState.update { it.copy(filters = filters) }

    fun createTask(
        spaceId: String,
        title: String,
        description: String?,
        assigneeId: String?,
        dueAt: Instant?,
        priority: TaskPriority,
    ) {
        if (!validate(title, description, assigneeId)) return
        runAction(TaskUiMessage.TaskCreated) {
            taskRepository.createTask(
                spaceId,
                title.trim(),
                description.normalized(),
                assigneeId!!,
                dueAt,
                priority,
            )
        }
    }

    fun updateTask(
        spaceId: String,
        taskId: String,
        title: String,
        description: String?,
        assigneeId: String?,
        dueAt: Instant?,
        priority: TaskPriority,
    ) {
        if (!validate(title, description, assigneeId)) return
        runAction(TaskUiMessage.TaskUpdated) {
            taskRepository.updateTask(
                spaceId,
                taskId,
                title.trim(),
                description.normalized(),
                assigneeId!!,
                dueAt,
                priority,
            )
        }
    }

    fun setCompleted(spaceId: String, taskId: String, completed: Boolean) = runAction(
        if (completed) TaskUiMessage.TaskCompleted else TaskUiMessage.TaskReopened,
    ) { taskRepository.setCompleted(spaceId, taskId, completed) }

    fun deleteTask(spaceId: String, taskId: String) = runAction(TaskUiMessage.TaskDeleted) {
        taskRepository.deleteTask(spaceId, taskId)
    }

    fun showInvalidDate() = showError(TaskUiMessage.InvalidDate)

    fun clearFeedback() = mutableUiState.update { it.copy(error = null, notice = null) }

    private fun validate(title: String, description: String?, assigneeId: String?): Boolean {
        val error = TaskValidation.validate(title, description, assigneeId) ?: return true
        showError(error)
        return false
    }

    private fun showError(message: TaskUiMessage) = mutableUiState.update {
        it.copy(isSaving = false, error = message, notice = null)
    }

    private fun runAction(notice: TaskUiMessage, action: suspend () -> Unit) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update { it.copy(isSaving = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                action()
                mutableUiState.update { it.copy(isSaving = false, notice = notice) }
            } catch (error: Throwable) {
                showError(error.toUiMessage())
            }
        }
    }
}

private fun String?.normalized() = this?.trim()?.takeIf(String::isNotEmpty)

private fun Throwable.toUiMessage() = when ((this as? TaskRepositoryException)?.failure) {
    TaskFailure.TitleRequired -> TaskUiMessage.TitleRequired
    TaskFailure.TitleTooLong -> TaskUiMessage.TitleTooLong
    TaskFailure.DescriptionTooLong -> TaskUiMessage.DescriptionTooLong
    TaskFailure.AssigneeRequired -> TaskUiMessage.AssigneeRequired
    TaskFailure.InvalidAssignee -> TaskUiMessage.InvalidAssignee
    TaskFailure.NotAuthenticated -> TaskUiMessage.NotAuthenticated
    TaskFailure.EmailNotVerified -> TaskUiMessage.EmailNotVerified
    TaskFailure.SpaceNotFound -> TaskUiMessage.SpaceNotFound
    TaskFailure.TaskNotFound -> TaskUiMessage.TaskNotFound
    TaskFailure.PermissionDenied -> TaskUiMessage.PermissionDenied
    TaskFailure.Network -> TaskUiMessage.NetworkError
    TaskFailure.Unknown, null -> TaskUiMessage.UnexpectedError
}
