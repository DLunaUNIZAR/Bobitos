package com.dlunaunizar.bobitos.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.failed
import com.dlunaunizar.bobitos.core.common.started
import com.dlunaunizar.bobitos.core.common.succeeded
import com.dlunaunizar.bobitos.core.common.withEditorSaveTimeout
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import com.dlunaunizar.bobitos.data.repository.ExerciseRepository
import com.dlunaunizar.bobitos.data.repository.RoutineFailure
import com.dlunaunizar.bobitos.data.repository.RoutineRepository
import com.dlunaunizar.bobitos.data.repository.RoutineRepositoryException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val repository: RoutineRepository,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(RoutinesUiState())
    val uiState: StateFlow<RoutinesUiState> = mutableUiState.asStateFlow()

    private var globalJob: Job? = null
    private var mineJob: Job? = null
    private var exercisesJob: Job? = null
    private var observing = false

    fun observe() {
        if (observing) return
        observing = true
        mutableUiState.update { it.copy(isAdmin = repository.isCurrentUserRoutineAdmin()) }
        globalJob = viewModelScope.launch {
            repository.globalRoutines()
                .catch { error -> mutableUiState.update { it.copy(global = UiState.Error(error.message)) } }
                .collect { list -> mutableUiState.update { it.copy(global = UiState.Content(list)) } }
        }
        mineJob = viewModelScope.launch {
            repository.myRoutines()
                .catch { error -> mutableUiState.update { it.copy(mine = UiState.Error(error.message)) } }
                .collect { list -> mutableUiState.update { it.copy(mine = UiState.Content(list)) } }
        }
    }

    // Catálogo de ejercicios para el editor de rutina. Diferido: lo pide el editor al aparecer. Idempotente;
    // un fallo se ignora (el selector quedará vacío).
    fun observeExerciseCatalog() {
        if (exercisesJob?.isActive == true) return
        exercisesJob = viewModelScope.launch {
            exerciseRepository.catalog()
                .catch { mutableUiState.update { it.copy(exercises = emptyList()) } }
                .collect { list -> mutableUiState.update { it.copy(exercises = list) } }
        }
    }

    fun stopObserving() {
        globalJob?.cancel()
        mineJob?.cancel()
        exercisesJob?.cancel()
        globalJob = null
        mineJob = null
        exercisesJob = null
        observing = false
    }

    fun setQuery(query: String) = mutableUiState.update { it.copy(query = query) }

    fun createRoutine(
        visibility: RoutineVisibility,
        title: String,
        description: String?,
        exercises: List<RoutineExercise>,
    ) {
        if (!validate(title, description)) return
        runAction(RoutineUiMessage.Saved, editor = true) {
            repository.createRoutine(visibility, title.trim(), description.normalized(), exercises)
        }
    }

    fun updateRoutine(routineId: String, title: String, description: String?, exercises: List<RoutineExercise>) {
        if (!validate(title, description)) return
        runAction(RoutineUiMessage.Saved, editor = true) {
            repository.updateRoutine(routineId, title.trim(), description.normalized(), exercises)
        }
    }

    fun deleteRoutine(routineId: String) {
        runAction(RoutineUiMessage.Deleted) { repository.deleteRoutine(routineId) }
    }

    fun consumeEditorSave() = mutableUiState.update { it.copy(editorSave = EditorSaveStatus.IDLE) }

    fun clearFeedback() = mutableUiState.update { it.copy(error = null, notice = null) }

    private fun validate(title: String, description: String?): Boolean {
        val error = RoutinesValidation.validate(title, description) ?: return true
        showError(error, editor = true)
        return false
    }

    private fun showError(message: RoutineUiMessage, editor: Boolean = false) = mutableUiState.update {
        it.copy(isSaving = false, error = message, notice = null, editorSave = it.editorSave.failed(editor))
    }

    private fun runAction(successNotice: RoutineUiMessage, editor: Boolean = false, action: suspend () -> Unit) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update {
            it.copy(isSaving = true, error = null, notice = null, editorSave = it.editorSave.started(editor))
        }
        viewModelScope.launch {
            runCatching { withEditorSaveTimeout(editor) { action() } }
                .onSuccess {
                    mutableUiState.update {
                        it.copy(isSaving = false, notice = successNotice, editorSave = it.editorSave.succeeded(editor))
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
    RoutineUiMessage.SaveTimeout
} else {
    toRepositoryUiMessage()
}

private fun Throwable.toRepositoryUiMessage() = when ((this as? RoutineRepositoryException)?.failure) {
    RoutineFailure.TitleRequired -> RoutineUiMessage.TitleRequired
    RoutineFailure.TitleTooLong -> RoutineUiMessage.TitleTooLong
    RoutineFailure.DescriptionTooLong -> RoutineUiMessage.DescriptionTooLong
    RoutineFailure.NotAuthenticated -> RoutineUiMessage.NotAuthenticated
    RoutineFailure.EmailNotVerified -> RoutineUiMessage.EmailNotVerified
    RoutineFailure.RoutineNotFound -> RoutineUiMessage.NotFound
    RoutineFailure.PermissionDenied -> RoutineUiMessage.PermissionDenied
    RoutineFailure.Network -> RoutineUiMessage.NetworkError
    RoutineFailure.Unknown, null -> RoutineUiMessage.UnexpectedError
}
