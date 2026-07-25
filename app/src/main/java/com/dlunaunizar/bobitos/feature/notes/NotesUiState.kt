package com.dlunaunizar.bobitos.feature.notes

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Note

data class NotesUiState(
    val notes: UiState<List<Note>> = UiState.Loading,
    val isSaving: Boolean = false,
    val error: NoteUiMessage? = null,
    val notice: NoteUiMessage? = null,
)

// Mensajes de UI del módulo Notas: validación, fallos del repositorio y avisos de éxito.
// La pantalla los mapea a recursos de string.
enum class NoteUiMessage {
    TitleRequired,
    TitleTooLong,
    BodyTooLong,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    NoteNotFound,
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    NoteAdded,
    NoteUpdated,
    NoteDeleted,
    NotePinned,
    NoteUnpinned,
}
