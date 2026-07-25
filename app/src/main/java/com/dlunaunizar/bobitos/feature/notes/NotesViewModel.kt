package com.dlunaunizar.bobitos.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.data.repository.NoteFailure
import com.dlunaunizar.bobitos.data.repository.NoteRepository
import com.dlunaunizar.bobitos.data.repository.NoteRepositoryException
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
class NotesViewModel @Inject constructor(private val repository: NoteRepository) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = mutableUiState.asStateFlow()
    private var notesJob: Job? = null
    private var observedSpaceId: String? = null

    fun observe(spaceId: String) {
        if (spaceId == observedSpaceId && notesJob?.isActive == true) return
        notesJob?.cancel()
        observedSpaceId = spaceId
        mutableUiState.update { it.copy(notes = UiState.Loading) }
        notesJob = viewModelScope.launch {
            repository.notes(spaceId)
                .catch { error -> mutableUiState.update { it.copy(notes = UiState.Error(error.message)) } }
                .collect { notes -> mutableUiState.update { it.copy(notes = UiState.Content(notes)) } }
        }
    }

    fun stopObserving() {
        notesJob?.cancel()
        notesJob = null
        observedSpaceId = null
    }

    fun addNote(title: String, body: String?) {
        val spaceId = observedSpaceId ?: return
        if (!validate(title, body)) return
        runAction(NoteUiMessage.NoteAdded) { repository.addNote(spaceId, title.trim(), body.normalized()) }
    }

    fun updateNote(noteId: String, title: String, body: String?) {
        val spaceId = observedSpaceId ?: return
        if (!validate(title, body)) return
        runAction(NoteUiMessage.NoteUpdated) { repository.updateNote(spaceId, noteId, title.trim(), body.normalized()) }
    }

    fun setPinned(noteId: String, pinned: Boolean) {
        val spaceId = observedSpaceId ?: return
        runAction(if (pinned) NoteUiMessage.NotePinned else NoteUiMessage.NoteUnpinned) {
            repository.setPinned(spaceId, noteId, pinned)
        }
    }

    fun deleteNote(noteId: String) {
        val spaceId = observedSpaceId ?: return
        // El borrado va tras un diálogo de confirmación; el aviso «Nota eliminada» lo da el snackbar.
        runAction(NoteUiMessage.NoteDeleted) { repository.deleteNote(spaceId, noteId) }
    }

    fun clearFeedback() = mutableUiState.update { it.copy(error = null, notice = null) }

    private fun validate(title: String, body: String?): Boolean {
        val error = NoteValidation.validate(title, body) ?: return true
        showError(error)
        return false
    }

    private fun showError(message: NoteUiMessage) = mutableUiState.update {
        it.copy(isSaving = false, error = message, notice = null)
    }

    private fun runAction(notice: NoteUiMessage?, action: suspend () -> Unit) {
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

private fun Throwable.toUiMessage() = when ((this as? NoteRepositoryException)?.failure) {
    NoteFailure.TitleRequired -> NoteUiMessage.TitleRequired
    NoteFailure.TitleTooLong -> NoteUiMessage.TitleTooLong
    NoteFailure.BodyTooLong -> NoteUiMessage.BodyTooLong
    NoteFailure.NotAuthenticated -> NoteUiMessage.NotAuthenticated
    NoteFailure.EmailNotVerified -> NoteUiMessage.EmailNotVerified
    NoteFailure.SpaceNotFound -> NoteUiMessage.SpaceNotFound
    NoteFailure.NoteNotFound -> NoteUiMessage.NoteNotFound
    NoteFailure.PermissionDenied -> NoteUiMessage.PermissionDenied
    NoteFailure.Network -> NoteUiMessage.NetworkError
    NoteFailure.Unknown, null -> NoteUiMessage.UnexpectedError
}
