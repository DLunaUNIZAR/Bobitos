package com.dlunaunizar.bobitos.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.SaveFeedbackControls
import com.dlunaunizar.bobitos.core.common.SaveRunner
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.UndoOffer
import com.dlunaunizar.bobitos.core.common.UndoOfferControls
import com.dlunaunizar.bobitos.core.common.UndoOffers
import com.dlunaunizar.bobitos.core.common.Undoable
import com.dlunaunizar.bobitos.core.common.launchPerIdWrite
import com.dlunaunizar.bobitos.core.common.toFailureKind
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskRecurrence
import com.dlunaunizar.bobitos.core.model.TaskType
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.TaskDates
import com.dlunaunizar.bobitos.data.repository.TaskFailure
import com.dlunaunizar.bobitos.data.repository.TaskRepository
import com.dlunaunizar.bobitos.data.repository.TaskRepositoryException
import com.dlunaunizar.bobitos.data.repository.completedWith
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
    private val saver =
        SaveRunner(viewModelScope, mutableUiState, TasksUiState::save, { copy(save = it) }, Throwable::toUiMessage)
    val feedback: SaveFeedbackControls get() = saver
    private var tasksJob: Job? = null
    private var membersJob: Job? = null
    private val undo = UndoOffers<TaskItem>(viewModelScope)
    val undoOffers: StateFlow<List<UndoOffer<TaskItem>>> = undo.offers
    val undoControls: UndoOfferControls get() = undo
    private val completingIds = mutableSetOf<String>()

    fun observe(spaceId: String) {
        stopObserving()
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
        // Un alta desde la hoja ofrece «Deshacer» (con el espacio de ahora) en lugar del aviso de tarea creada.
        saver.run<CreatedItem>(editor = true, afterSuccess = { undo.offer(Undoable.Created(spaceId, it)) }) {
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
        saver.run(notice = TaskUiMessage.TaskUpdated, editor = true) {
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

    // Completar y reabrir no pasan por el `SaveRunner` (no encienden «Guardando…»): cada tarea lleva su propia escritura en curso, así que
    // varias seguidas salen todas al momento y no apagan el resto de la pantalla. Devuelve false (sin escribir) si esa
    // tarea ya tiene una escritura en curso. [onFinished] recibe cómo acabó. Si completar termina bien, ofrece
    // «Deshacer» con la tarea de antes y como la dejó la escritura. Al completar no hay aviso propio; al reabrir, el
    // de siempre.
    fun setCompleted(
        spaceId: String,
        task: TaskItem,
        completed: Boolean,
        onFinished: (Boolean) -> Unit = {},
    ): Boolean {
        if (task.id in completingIds) return false
        var dates: TaskDates? = null
        return viewModelScope.launchPerIdWrite(
            completingIds,
            task.id,
            onFinished = onFinished,
            onSuccess = {
                if (completed) {
                    dates?.let { undo.offer(Undoable.Completed(spaceId, task, task.completedWith(it))) }
                } else {
                    saver.showNotice(TaskUiMessage.TaskReopened)
                }
            },
            onFailure = { error -> saver.showError(error.toUiMessage()) },
        ) { dates = taskRepository.setCompleted(spaceId, task.id, completed) }
    }

    // El espacio se lee al llamar. Sin notice: el feedback lo da el Snackbar de la pantalla, cuando el borrado acaba
    // bien (así el «Deshacer» no se ofrece para un borrado fallido ni se pulsa mientras el borrado sigue en curso).
    fun deleteTask(spaceId: String, task: TaskItem) {
        saver.run(afterSuccess = { undo.offer(Undoable.Deleted(spaceId, task)) }) {
            taskRepository.deleteTask(spaceId, task.id)
        }
    }

    // Única entrada de «Deshacer»: siempre en el espacio donde pasó. No es un guardado del editor ni lleva aviso
    // propio; si falla (también si la tarea ya no existe o la cambió otro), sale el error.
    fun undo(undoable: Undoable<TaskItem>) {
        val spaceId = undoable.spaceId ?: return
        saver.run(force = true) {
            when (undoable) {
                is Undoable.Created -> taskRepository.undoCreateTask(spaceId, undoable.item.id)
                is Undoable.Deleted -> taskRepository.restoreTask(spaceId, undoable.item)
                is Undoable.Completed -> taskRepository.restoreCompletion(
                    spaceId,
                    undoable.previous,
                    undoable.completed,
                )
            }
        }
    }

    // Solo se llama desde el editor.
    fun showInvalidDate() = saver.showEditorError(TaskUiMessage.InvalidDate)

    private fun validate(title: String, description: String?): Boolean {
        val error = TaskValidation.validate(title, description) ?: return true
        saver.showEditorError(error)
        return false
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
    TaskFailure.TaskChanged -> TaskUiMessage.TaskChanged
    TaskFailure.PermissionDenied -> TaskUiMessage.PermissionDenied
    TaskFailure.Network -> TaskUiMessage.NetworkError
    TaskFailure.Unknown, null -> TaskUiMessage.UnexpectedError
}
