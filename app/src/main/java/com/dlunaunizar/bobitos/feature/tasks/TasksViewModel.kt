package com.dlunaunizar.bobitos.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.failed
import com.dlunaunizar.bobitos.core.common.launchPerIdWrite
import com.dlunaunizar.bobitos.core.common.started
import com.dlunaunizar.bobitos.core.common.succeeded
import com.dlunaunizar.bobitos.core.common.toFailureKind
import com.dlunaunizar.bobitos.core.common.withEditorSaveTimeout
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
    private var activeSpaceId: String? = null
    private val completingIds = mutableSetOf<String>()

    fun observe(spaceId: String) {
        stopObserving()
        activeSpaceId = spaceId
        mutableUiState.update { it.copy(tasks = UiState.Loading, members = UiState.Loading) }
        tasksJob = viewModelScope.launch {
            taskRepository.tasks(spaceId).catch { error ->
                mutableUiState.update { it.copy(tasks = UiState.Error(error.toFailureKind())) }
            }.collect { tasks ->
                mutableUiState.update { it.copy(tasks = UiState.Content(tasks)) }
            }
        }
        membersJob = viewModelScope.launch {
            spaceRepository.members(spaceId).catch { error ->
                mutableUiState.update { it.copy(members = UiState.Error(error.toFailureKind())) }
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

    fun clearFilters() = setFilters(TaskFilters())

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
        val name = title.trim()
        activeSpaceId = spaceId
        // Un alta desde la hoja deja `created` (con «Deshacer») en lugar del aviso de tarea creada.
        runAction(null, editor = true) {
            val id = taskRepository.createTask(
                spaceId,
                name,
                description.normalized(),
                assigneeId,
                dueAt,
                priority,
                type,
                recurrence,
                startAt,
            )
            CreatedItem(id, name)
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

    // Completar y reabrir no pasan por la puerta de `isSaving`: cada tarea lleva su propia escritura en curso, así que
    // varias seguidas salen todas al momento y no apagan el resto de la pantalla. Devuelve false (sin escribir) si esa
    // tarea ya tiene una escritura en curso. [onFinished] recibe cómo acabó; [onCompleted] recibe la tarea tal como
    // estaba (estado y fechas) solo si completar termina bien, y la pantalla ofrece «Deshacer» con ella. Al completar no
    // hay aviso propio; al reabrir, el de siempre.
    fun setCompleted(
        spaceId: String,
        task: TaskItem,
        completed: Boolean,
        onFinished: (Boolean) -> Unit = {},
        onCompleted: (TaskItem) -> Unit = {},
    ): Boolean {
        if (task.id in completingIds) return false
        mutableUiState.update { it.copy(error = null, notice = null) }
        return viewModelScope.launchPerIdWrite(
            completingIds,
            task.id,
            onFinished = { ok ->
                onFinished(ok)
                if (ok && completed) onCompleted(task)
            },
            onSuccess = { if (!completed) mutableUiState.update { it.copy(notice = TaskUiMessage.TaskReopened) } },
            onFailure = { error -> mutableUiState.update { it.copy(error = error.toUiMessage(), notice = null) } },
        ) { taskRepository.setCompleted(spaceId, task.id, completed) }
    }

    // «Deshacer» completar: la tarea vuelve a pendiente con las fechas de [previous]. Sin aviso; si falla (también si
    // la tarea ya no existe), sale el error.
    fun undoCompletion(spaceId: String, previous: TaskItem) = runAction(null, force = true) {
        taskRepository.restoreCompletion(spaceId, previous)
    }

    // onDeleted se llama solo si el borrado termina bien: así el «Deshacer» no se ofrece para un borrado fallido ni
    // se pulsa mientras el borrado sigue en curso. Sin notice: el feedback lo da el Snackbar de la pantalla.
    fun deleteTask(spaceId: String, taskId: String, onDeleted: () -> Unit = {}) {
        runAction(null, onSuccess = onDeleted) { taskRepository.deleteTask(spaceId, taskId) }
    }

    // «Deshacer» el borrado: la tarea vuelve con su id y su estado, al espacio en que estaba. No es un guardado del
    // editor ni lleva aviso propio; si falla, sale el error.
    fun restoreTask(spaceId: String, task: TaskItem) = runAction(null, force = true) {
        taskRepository.restoreTask(spaceId, task)
    }

    fun consumeCreated() = mutableUiState.update { it.copy(created = null) }

    // «Deshacer» el alta: borra justo esa tarea con el borrado de siempre (sin aviso; si falla, sale el error).
    fun undoCreate(id: String) {
        val spaceId = activeSpaceId ?: return
        runAction(null, force = true) { taskRepository.deleteTask(spaceId, id) }
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

    // Si la acción devuelve un [CreatedItem], es un alta desde la hoja y queda en `created`.
    private fun runAction(
        notice: TaskUiMessage?,
        editor: Boolean = false,
        force: Boolean = false,
        onSuccess: () -> Unit = {},
        action: suspend () -> Any?,
    ) {
        if (mutableUiState.value.isSaving && !force) return
        mutableUiState.update {
            it.copy(isSaving = true, error = null, notice = null, editorSave = it.editorSave.started(editor))
        }
        viewModelScope.launch {
            runCatching { withEditorSaveTimeout(editor) { action() } }
                .onSuccess { result ->
                    mutableUiState.update {
                        it.copy(
                            isSaving = false,
                            notice = notice,
                            created = result as? CreatedItem,
                            editorSave = it.editorSave.succeeded(editor),
                        )
                    }
                    onSuccess()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    showError(error.toUiMessage(), editor)
                }
        }
    }
}

private fun String?.normalized() = this?.trim()?.takeIf(String::isNotEmpty)

internal fun Throwable.toUiMessage() = if (this is SaveTimeoutException) {
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
