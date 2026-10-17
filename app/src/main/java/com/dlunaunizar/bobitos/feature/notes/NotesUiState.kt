package com.dlunaunizar.bobitos.feature.notes

import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Note

data class NotesUiState(
    val notes: UiState<List<Note>> = UiState.Loading,
    val isSaving: Boolean = false,
    val editorSave: EditorSaveStatus = EditorSaveStatus.IDLE,
    val error: NoteUiMessage? = null,
    val notice: NoteUiMessage? = null,
    // Alta recién hecha desde la hoja: la pantalla la avisa con «Deshacer» y la consume.
    val created: CreatedItem? = null,
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
    SaveTimeout,
    NoteAdded,
    NoteUpdated,
    NotePinned,
    NoteUnpinned,
}
