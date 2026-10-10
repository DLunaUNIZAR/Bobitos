package com.dlunaunizar.bobitos.feature.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.failed
import com.dlunaunizar.bobitos.core.common.started
import com.dlunaunizar.bobitos.core.common.succeeded
import com.dlunaunizar.bobitos.core.common.withSaveTimeout
import com.dlunaunizar.bobitos.core.model.ExerciseInput
import com.dlunaunizar.bobitos.core.model.slug
import com.dlunaunizar.bobitos.data.repository.ExerciseFailure
import com.dlunaunizar.bobitos.data.repository.ExerciseRepository
import com.dlunaunizar.bobitos.data.repository.ExerciseRepositoryException
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
class ExercisesViewModel @Inject constructor(private val repository: ExerciseRepository) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ExercisesUiState())
    val uiState: StateFlow<ExercisesUiState> = mutableUiState.asStateFlow()

    private var catalogJob: Job? = null
    private var observing = false

    fun observe() {
        if (observing) return
        observing = true
        mutableUiState.update {
            it.copy(isAdmin = repository.isCurrentUserCatalogAdmin(), currentUid = repository.currentUserId())
        }
        catalogJob = viewModelScope.launch {
            repository.catalog()
                .catch { error -> mutableUiState.update { it.copy(catalog = UiState.Error(error.message)) } }
                .collect { list -> mutableUiState.update { it.copy(catalog = UiState.Content(list)) } }
        }
    }

    fun stopObserving() {
        catalogJob?.cancel()
        catalogJob = null
        observing = false
    }

    fun createExercise(input: ExerciseInput) {
        val trimmed = input.name.trim()
        if (trimmed.isEmpty()) {
            showError(ExerciseUiMessage.NameRequired, editor = true)
            return
        }
        if (catalogContains(slug(trimmed))) {
            showError(ExerciseUiMessage.AlreadyExists, editor = true)
            return
        }
        runAction(ExerciseUiMessage.Saved, editor = true) { repository.createExercise(input.copy(name = trimmed)) }
    }

    fun updateExercise(id: String, input: ExerciseInput) {
        if (input.name.trim().isEmpty()) {
            showError(ExerciseUiMessage.NameRequired, editor = true)
            return
        }
        runAction(ExerciseUiMessage.Saved, editor = true) {
            repository.updateExercise(id, input.copy(name = input.name.trim()))
        }
    }

    fun deleteExercise(id: String) {
        runAction(ExerciseUiMessage.Deleted) { repository.deleteExercise(id) }
    }

    fun consumeEditorSave() = mutableUiState.update { it.copy(editorSave = EditorSaveStatus.IDLE) }

    fun clearFeedback() = mutableUiState.update { it.copy(error = null, notice = null) }

    private fun catalogContains(id: String): Boolean =
        (mutableUiState.value.catalog as? UiState.Content)?.value?.any { it.id == id } == true

    private fun showError(message: ExerciseUiMessage, editor: Boolean = false) = mutableUiState.update {
        it.copy(isSaving = false, error = message, notice = null, editorSave = it.editorSave.failed(editor))
    }

    private fun runAction(successNotice: ExerciseUiMessage, editor: Boolean = false, action: suspend () -> Unit) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update {
            it.copy(isSaving = true, error = null, notice = null, editorSave = it.editorSave.started(editor))
        }
        viewModelScope.launch {
            runCatching { withSaveTimeout { action() } }
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

private fun Throwable.toUiMessage(): ExerciseUiMessage = if (this is SaveTimeoutException) {
    ExerciseUiMessage.SaveTimeout
} else {
    toRepositoryUiMessage()
}

private fun Throwable.toRepositoryUiMessage(): ExerciseUiMessage =
    when ((this as? ExerciseRepositoryException)?.failure) {
        ExerciseFailure.NameRequired -> ExerciseUiMessage.NameRequired
        ExerciseFailure.NameTooLong -> ExerciseUiMessage.NameTooLong
        ExerciseFailure.MuscleGroupTooLong -> ExerciseUiMessage.MuscleGroupTooLong
        ExerciseFailure.DescriptionTooLong -> ExerciseUiMessage.DescriptionTooLong
        ExerciseFailure.NotAuthenticated -> ExerciseUiMessage.NotAuthenticated
        ExerciseFailure.EmailNotVerified -> ExerciseUiMessage.EmailNotVerified
        ExerciseFailure.ExerciseNotFound -> ExerciseUiMessage.NotFound
        ExerciseFailure.PermissionDenied -> ExerciseUiMessage.PermissionDenied
        ExerciseFailure.AlreadyExists -> ExerciseUiMessage.AlreadyExists
        ExerciseFailure.Network -> ExerciseUiMessage.NetworkError
        ExerciseFailure.Unknown, null -> ExerciseUiMessage.UnexpectedError
    }
