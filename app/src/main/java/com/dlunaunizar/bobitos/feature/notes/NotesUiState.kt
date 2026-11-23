package com.dlunaunizar.bobitos.feature.notes

import com.dlunaunizar.bobitos.core.common.SaveState
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Note

data class NotesUiState(
    val notes: UiState<List<Note>> = UiState.Loading,
    val save: SaveState<NoteUiMessage, NoteUiMessage> = SaveState(),
    /** Hay notas más antiguas que las que se leen: la lista lo avisa al final. */
    val notesTruncated: Boolean = false,
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
    NoteChanged,
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    SaveTimeout,
    NoteAdded,
    NoteUpdated,
    NotePinned,
    NoteUnpinned,
}
