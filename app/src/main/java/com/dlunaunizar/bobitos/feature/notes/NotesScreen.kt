package com.dlunaunizar.bobitos.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.messageRes
import com.dlunaunizar.bobitos.core.designsystem.component.AddFab
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.designsystem.component.ClickableCard
import com.dlunaunizar.bobitos.core.designsystem.component.CreatedUndoEffect
import com.dlunaunizar.bobitos.core.designsystem.component.EditorSaveEffect
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.MoreOptionsButton
import com.dlunaunizar.bobitos.core.designsystem.component.SaveFeedbackEffect
import com.dlunaunizar.bobitos.core.designsystem.component.ScreenScaffold
import com.dlunaunizar.bobitos.core.designsystem.component.errorSupportingText
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.designsystem.component.rememberEditorSlot
import com.dlunaunizar.bobitos.core.designsystem.component.rememberFormValidationState
import com.dlunaunizar.bobitos.core.designsystem.rememberFabExpanded
import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.designsystem.theme.moduleTint
import com.dlunaunizar.bobitos.core.model.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onBack: () -> Unit,
    canWrite: Boolean,
    modifier: Modifier = Modifier,
    spaceId: String? = null,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(spaceId) {
        if (spaceId != null) viewModel.observe(spaceId)
        onDispose { viewModel.stopObserving() }
    }
    // El editor sobrevive a una rotación: se guarda el id de la nota, no el objeto.
    var editorNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    // Sin espacio activo no se puede escribir (las escrituras usan observedSpaceId): se desactivan
    // los controles para no perder cambios en silencio si el espacio deja de estar disponible.
    val enabled = canWrite && spaceId != null && !state.save.isSaving
    val closeEditor: () -> Unit = {
        // Cerrar tras un guardado fallido descarta ese error (ya se veía en la hoja).
        viewModel.feedback.resetEditor()
        editorVisible = false
    }

    val listState = rememberLazyListState()
    ScreenScaffold(
        module = AppModule.NOTES,
        title = stringResource(R.string.notes_title),
        onBack = onBack,
        modifier = modifier,
        floatingActionButton = {
            if (canWrite && spaceId != null) {
                val fabExpanded = rememberFabExpanded(listState)
                AddFab(
                    label = R.string.notes_add,
                    expanded = fabExpanded,
                    onClick = {
                        editorNoteId = null
                        editorVisible = true
                    },
                )
            }
        },
    ) { innerPadding ->
        val snackbar = LocalSnackbarHostState.current
        val scope = rememberCoroutineScope()
        val deletedMessage = stringResource(R.string.notes_notice_deleted)
        val undoLabel = stringResource(R.string.undo)
        SaveFeedbackEffect(
            save = state.save,
            controls = viewModel.feedback,
            covered = editorVisible,
            errorText = { stringResource(it.stringRes()) },
            noticeText = { stringResource(it.stringRes()) },
        )
        CreatedUndoEffect(state.save.created, viewModel.feedback::consumeCreated) { viewModel.undoCreate(it.id) }
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            when (val notes = state.notes) {
                UiState.Loading -> LoadingState(Modifier.weight(1f))
                is UiState.Error -> ErrorState(
                    Modifier.weight(1f),
                    message = stringResource(notes.failure.messageRes()),
                    onRetry = spaceId?.let { id -> { viewModel.observe(id) } },
                )
                is UiState.Content -> if (notes.value.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Rounded.StickyNote2,
                        title = stringResource(R.string.notes_empty),
                        description = stringResource(R.string.notes_empty_description),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = Spacing.lg,
                            top = Spacing.lg,
                            end = Spacing.lg,
                            bottom = Spacing.fabClearance,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(notes.value, key = Note::id) { note ->
                            val openEditor = {
                                editorNoteId = note.id
                                editorVisible = true
                            }
                            NoteCard(
                                note = note,
                                enabled = enabled,
                                onClick = openEditor.takeIf { enabled },
                                onTogglePin = { viewModel.setPinned(note.id, !note.pinned) },
                                onEdit = openEditor,
                                onDelete = {
                                    // El espacio se captura al borrar: «Deshacer» devuelve la nota a su espacio.
                                    val deletedFrom = spaceId
                                    if (deletedFrom != null) {
                                        viewModel.deleteNote(note.id) {
                                            scope.launchUndo(snackbar, deletedMessage, undoLabel) {
                                                viewModel.restoreNote(deletedFrom, note)
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    EditorSaveEffect(
        editor = state.save.editor,
        editorOpen = editorVisible,
        controls = viewModel.feedback,
        onClose = { editorVisible = false },
    )
    rememberEditorSlot(
        open = editorVisible,
        id = editorNoteId,
        items = (state.notes as? UiState.Content)?.value,
        idOf = Note::id,
        onGone = closeEditor,
    )?.let { slot ->
        NoteEditor(
            note = slot.item,
            saving = state.save.isSaving,
            errorMessage = state.save.editorError?.let { stringResource(it.stringRes()) },
            onDismiss = closeEditor,
            onSave = { title, body ->
                slot.item?.let { viewModel.updateNote(it.id, title, body) }
                    ?: viewModel.addNote(title, body)
            },
        )
    }
}

@Composable
internal fun NoteCard(
    note: Note,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    // Sin permiso de escritura es una Card normal: `enabled = false` cambiaría su color.
    val content: @Composable () -> Unit = {
        Row(
            Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(note.title, style = MaterialTheme.typography.titleMedium)
                note.body?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onTogglePin, enabled = enabled) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = stringResource(if (note.pinned) R.string.notes_unpin else R.string.notes_pin),
                    tint = if (note.pinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Box {
                MoreOptionsButton(itemName = note.title, onClick = { menu = true }, enabled = enabled)
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notes_edit)) },
                        onClick = {
                            menu = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notes_delete)) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
    // La fijada lleva el tinte de Notas; las demás, el contenedor normal de la tarjeta.
    val tint = moduleTint(AppModule.NOTES)
    ClickableCard(onClick, Modifier.fillMaxWidth(), containerColor = tint.takeIf { note.pinned }) { content() }
}

@Composable
private fun NoteEditor(
    note: Note?,
    saving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    val initial = NoteDraft.of(note)
    var draft by rememberSaveable(note?.id) { mutableStateOf(initial) }
    val validation = NoteValidation.validate(draft.title, draft.body)
    val form = rememberFormValidationState()
    val titleFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    val bodyInvalid = validation == NoteUiMessage.BodyTooLong
    val titleError = form.visible(validation?.takeUnless { bodyInvalid })
    val bodyError = form.visible(validation?.takeIf { bodyInvalid })
    BobitosFormSheet(
        title = stringResource(if (note == null) R.string.notes_add_title else R.string.notes_edit_title),
        confirmLabel = stringResource(R.string.save),
        saving = saving,
        dirty = { draft != initial },
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = {
            form.submit(
                isValid = validation == null,
                onValid = { onSave(draft.title, draft.body) },
                onInvalid = { (if (bodyInvalid) bodyFocus else titleFocus).requestFocus() },
            )
        },
    ) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = { draft = draft.copy(title = it) },
            label = { Text(stringResource(R.string.notes_title_label)) },
            singleLine = true,
            isError = titleError != null,
            supportingText = errorSupportingText(titleError?.let { stringResource(it.stringRes()) }),
            modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
        )
        OutlinedTextField(
            value = draft.body,
            onValueChange = { draft = draft.copy(body = it) },
            label = { Text(stringResource(R.string.notes_body_label)) },
            minLines = 3,
            isError = bodyError != null,
            supportingText = errorSupportingText(bodyError?.let { stringResource(it.stringRes()) }),
            modifier = Modifier.fillMaxWidth().focusRequester(bodyFocus),
        )
    }
}

private fun NoteUiMessage.stringRes() = when (this) {
    NoteUiMessage.TitleRequired -> R.string.notes_error_title_required
    NoteUiMessage.TitleTooLong -> R.string.notes_error_title_too_long
    NoteUiMessage.BodyTooLong -> R.string.notes_error_body_too_long
    NoteUiMessage.NotAuthenticated -> R.string.space_error_not_authenticated
    NoteUiMessage.EmailNotVerified -> R.string.space_error_email_not_verified
    NoteUiMessage.SpaceNotFound -> R.string.space_error_not_found
    NoteUiMessage.NoteNotFound -> R.string.notes_error_not_found
    NoteUiMessage.PermissionDenied -> R.string.space_error_permission_denied
    NoteUiMessage.NetworkError -> R.string.space_error_network
    NoteUiMessage.UnexpectedError -> R.string.space_error_unexpected
    NoteUiMessage.SaveTimeout -> R.string.write_timeout
    NoteUiMessage.NoteAdded -> R.string.notes_notice_added
    NoteUiMessage.NoteUpdated -> R.string.notes_notice_updated
    NoteUiMessage.NotePinned -> R.string.notes_notice_pinned
    NoteUiMessage.NoteUnpinned -> R.string.notes_notice_unpinned
}
