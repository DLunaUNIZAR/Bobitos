package com.dlunaunizar.bobitos.feature.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.slug
import com.dlunaunizar.bobitos.data.repository.ExerciseFailure
import com.dlunaunizar.bobitos.data.repository.ExerciseRepository
import com.dlunaunizar.bobitos.data.repository.ExerciseRepositoryException
import dagger.hilt.android.lifecycle.HiltViewModel
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

    fun setQuery(query: String) = mutableUiState.update { it.copy(query = query) }

    fun createExercise(name: String, type: ExerciseType, muscleGroup: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            showError(ExerciseUiMessage.NameRequired)
            return
        }
        if (catalogContains(slug(trimmed))) {
            showError(ExerciseUiMessage.AlreadyExists)
            return
        }
        runAction(ExerciseUiMessage.Saved) { repository.createExercise(trimmed, type, muscleGroup) }
    }

    fun updateExercise(id: String, name: String, type: ExerciseType, muscleGroup: String?) {
        if (name.trim().isEmpty()) {
            showError(ExerciseUiMessage.NameRequired)
            return
        }
        runAction(ExerciseUiMessage.Saved) { repository.updateExercise(id, name.trim(), type, muscleGroup) }
    }

    fun deleteExercise(id: String) {
        runAction(ExerciseUiMessage.Deleted) { repository.deleteExercise(id) }
    }

    fun clearFeedback() = mutableUiState.update { it.copy(error = null, notice = null) }

    private fun catalogContains(id: String): Boolean =
        (mutableUiState.value.catalog as? UiState.Content)?.value?.any { it.id == id } == true

    private fun showError(message: ExerciseUiMessage) = mutableUiState.update {
        it.copy(isSaving = false, error = message, notice = null)
    }

    private fun runAction(successNotice: ExerciseUiMessage, action: suspend () -> Unit) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update { it.copy(isSaving = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                action()
                mutableUiState.update { it.copy(isSaving = false, notice = successNotice) }
            } catch (error: Throwable) {
                showError(error.toUiMessage())
            }
        }
    }
}

private fun Throwable.toUiMessage(): ExerciseUiMessage = when ((this as? ExerciseRepositoryException)?.failure) {
    ExerciseFailure.NameRequired -> ExerciseUiMessage.NameRequired
    ExerciseFailure.NameTooLong -> ExerciseUiMessage.NameTooLong
    ExerciseFailure.MuscleGroupTooLong -> ExerciseUiMessage.MuscleGroupTooLong
    ExerciseFailure.NotAuthenticated -> ExerciseUiMessage.NotAuthenticated
    ExerciseFailure.EmailNotVerified -> ExerciseUiMessage.EmailNotVerified
    ExerciseFailure.ExerciseNotFound -> ExerciseUiMessage.NotFound
    ExerciseFailure.PermissionDenied -> ExerciseUiMessage.PermissionDenied
    ExerciseFailure.Network -> ExerciseUiMessage.NetworkError
    ExerciseFailure.Unknown, null -> ExerciseUiMessage.UnexpectedError
}
