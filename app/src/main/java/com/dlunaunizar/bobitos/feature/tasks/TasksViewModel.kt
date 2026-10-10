package com.dlunaunizar.bobitos.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.failed
import com.dlunaunizar.bobitos.core.common.started
import com.dlunaunizar.bobitos.core.common.succeeded
import com.dlunaunizar.bobitos.core.common.withSaveTimeout
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskRecurrence
import com.dlunaunizar.bobitos.core.model.TaskType
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.TaskFailure
import com.dlunaunizar.bobitos.data.repository.TaskRepository
import com.dlunaunizar.bobitos.data.repository.TaskRepositoryException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
        type: TaskType?,
        recurrence: TaskRecurrence?,
        startAt: Instant?,
    ) {
        if (!validate(title, description)) return
        runAction(TaskUiMessage.TaskCreated, editor = true) {
            taskRepository.createTask(
                spaceId,
                title.trim(),
                description.normalized(),
                assigneeId,
                dueAt,
                priority,
                type,
                recurrence,
                startAt,
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
        type: TaskType?,
        recurrence: TaskRecurrence?,
        startAt: Instant?,
    ) {
        if (!validate(title, description)) return
        runAction(TaskUiMessage.TaskUpdated, editor = true) {
            taskRepository.updateTask(
                spaceId,
                taskId,
                title.trim(),
                description.normalized(),
                assigneeId,
                dueAt,
                priority,
                type,
                recurrence,
                startAt,
            )
        }
    }

    fun setCompleted(spaceId: String, taskId: String, completed: Boolean) = runAction(
        if (completed) TaskUiMessage.TaskCompleted else TaskUiMessage.TaskReopened,
    ) { taskRepository.setCompleted(spaceId, taskId, completed) }

    // Sin notice: el feedback del borrado (con «Deshacer») lo da el Snackbar de la pantalla.
    fun deleteTask(spaceId: String, taskId: String) = runAction(null) {
        taskRepository.deleteTask(spaceId, taskId)
    }

    // Recrea una tarea borrada («Deshacer»): no es un guardado del editor, no mueve su estado.
    fun restoreTask(spaceId: String, task: TaskItem) = runAction(TaskUiMessage.TaskCreated) {
        taskRepository.createTask(
            spaceId,
            task.title,
            task.description,
            task.assigneeId,
            task.dueAt,
            task.priority,
            task.type,
            task.recurrence,
            task.startAt,
        )
    }

    fun consumeEditorSave() = mutableUiState.update { it.copy(editorSave = EditorSaveStatus.IDLE) }

    // Solo se llama desde el editor.
    fun showInvalidDate() = showError(TaskUiMessage.InvalidDate, editor = true)

    fun clearFeedback() = mutableUiState.update { it.copy(error = null, notice = null) }

    private fun validate(title: String, description: String?): Boolean {
        val error = TaskValidation.validate(title, description) ?: return true
        showError(error, editor = true)
        return false
    }

    private fun showError(message: TaskUiMessage, editor: Boolean = false) = mutableUiState.update {
        it.copy(isSaving = false, error = message, notice = null, editorSave = it.editorSave.failed(editor))
    }

    private fun runAction(notice: TaskUiMessage?, editor: Boolean = false, action: suspend () -> Unit) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update {
            it.copy(isSaving = true, error = null, notice = null, editorSave = it.editorSave.started(editor))
        }
        viewModelScope.launch {
            runCatching { withSaveTimeout { action() } }
                .onSuccess {
                    mutableUiState.update {
                        it.copy(isSaving = false, notice = notice, editorSave = it.editorSave.succeeded(editor))
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    showError(error.toUiMessage(), editor)
                }
        }
    }
}

private fun String?.normalized() = this?.trim()?.takeIf(String::isNotEmpty)

private fun Throwable.toUiMessage() = if (this is SaveTimeoutException) {
    TaskUiMessage.SaveTimeout
} else {
    toRepositoryUiMessage()
}

private fun Throwable.toRepositoryUiMessage() = when ((this as? TaskRepositoryException)?.failure) {
    TaskFailure.TitleRequired -> TaskUiMessage.TitleRequired
    TaskFailure.TitleTooLong -> TaskUiMessage.TitleTooLong
    TaskFailure.DescriptionTooLong -> TaskUiMessage.DescriptionTooLong
    TaskFailure.InvalidAssignee -> TaskUiMessage.InvalidAssignee
    TaskFailure.NotAuthenticated -> TaskUiMessage.NotAuthenticated
    TaskFailure.EmailNotVerified -> TaskUiMessage.EmailNotVerified
    TaskFailure.SpaceNotFound -> TaskUiMessage.SpaceNotFound
    TaskFailure.TaskNotFound -> TaskUiMessage.TaskNotFound
    TaskFailure.PermissionDenied -> TaskUiMessage.PermissionDenied
    TaskFailure.Network -> TaskUiMessage.NetworkError
    TaskFailure.Unknown, null -> TaskUiMessage.UnexpectedError
}
