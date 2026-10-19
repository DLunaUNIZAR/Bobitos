package com.dlunaunizar.bobitos.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.SaveFeedbackControls
import com.dlunaunizar.bobitos.core.common.SaveRunner
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.toFailureKind
import com.dlunaunizar.bobitos.core.model.Note
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
    private val saver =
        SaveRunner(viewModelScope, mutableUiState, NotesUiState::save, { copy(save = it) }, Throwable::toUiMessage)
    val feedback: SaveFeedbackControls get() = saver
    private var notesJob: Job? = null
    private var observedSpaceId: String? = null

    fun observe(spaceId: String) {
        if (spaceId == observedSpaceId && notesJob?.isActive == true) return
        notesJob?.cancel()
        observedSpaceId = spaceId
        mutableUiState.update { it.copy(notes = UiState.Loading) }
        notesJob = viewModelScope.launch {
            repository.notes(spaceId)
                .catch { error -> mutableUiState.update { it.copy(notes = UiState.Error(error.toFailureKind())) } }
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
        val name = title.trim()
        // Un alta desde la hoja deja `created` (con «Deshacer») en lugar del aviso de nota añadida.
        saver.create(editor = true) {
            CreatedItem(repository.addNote(spaceId, name, body.normalized()), name)
        }
    }

    fun updateNote(noteId: String, title: String, body: String?) {
        val spaceId = observedSpaceId ?: return
        if (!validate(title, body)) return
        saver.run(notice = NoteUiMessage.NoteUpdated, editor = true) {
            repository.updateNote(spaceId, noteId, title.trim(), body.normalized())
        }
    }

    fun setPinned(noteId: String, pinned: Boolean) {
        val spaceId = observedSpaceId ?: return
        saver.run(notice = if (pinned) NoteUiMessage.NotePinned else NoteUiMessage.NoteUnpinned) {
            repository.setPinned(spaceId, noteId, pinned)
        }
    }

    // onDeleted se llama solo si el borrado termina bien: así el «Deshacer» no se ofrece para un borrado
    // fallido. Sin aviso propio: el snackbar de «Deshacer» ya confirma el borrado.
    fun deleteNote(noteId: String, onDeleted: () -> Unit = {}) {
        val spaceId = observedSpaceId ?: return
        saver.run(afterSuccess = { onDeleted() }) { repository.deleteNote(spaceId, noteId) }
    }

    // «Deshacer» el borrado: el espacio es el de la nota al borrar, no el que se esté viendo ahora.
    fun restoreNote(spaceId: String, note: Note) {
        saver.run(force = true) { repository.restoreNote(spaceId, note) }
    }

    // «Deshacer» el alta: borra justo esa nota con el borrado de siempre (sin aviso; si falla, sale el error).
    fun undoCreate(id: String) {
        val spaceId = observedSpaceId ?: return
        saver.run(force = true) { repository.deleteNote(spaceId, id) }
    }

    private fun validate(title: String, body: String?): Boolean {
        val error = NoteValidation.validate(title, body) ?: return true
        saver.showEditorError(error)
        return false
    }
}

private fun String?.normalized() = this?.trim()?.takeIf(String::isNotEmpty)

private fun Throwable.toUiMessage() = if (this is SaveTimeoutException) {
    NoteUiMessage.SaveTimeout
} else {
    toRepositoryUiMessage()
}

private fun Throwable.toRepositoryUiMessage() = when ((this as? NoteRepositoryException)?.failure) {
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
