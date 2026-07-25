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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
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
    val snackbarHostState = remember { SnackbarHostState() }
    // Avisos de éxito como snackbar efímero (el banner queda para errores persistentes).
    val noticeMessage = state.notice?.let { stringResource(it.stringRes()) }
    LaunchedEffect(state.notice) {
        val text = noticeMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.clearFeedback()
    }
    var editorNote by remember { mutableStateOf<Note?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    // Sin espacio activo no se puede escribir (las escrituras usan observedSpaceId): se desactivan
    // los controles para no perder cambios en silencio si el espacio deja de estar disponible.
    val enabled = canWrite && spaceId != null && !state.isSaving

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (canWrite && spaceId != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editorNote = null
                        editorVisible = true
                    },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.notes_add)) },
                )
            }
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            state.error?.let { message -> NoteFeedbackBanner(message, viewModel::clearFeedback) }
            when (val notes = state.notes) {
                UiState.Loading -> LoadingState(Modifier.weight(1f))
                is UiState.Error -> ErrorState(Modifier.weight(1f), message = notes.message)
                is UiState.Content -> if (notes.value.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Rounded.StickyNote2,
                        title = stringResource(R.string.notes_empty),
                        description = stringResource(R.string.notes_empty_description),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(notes.value, key = Note::id) { note ->
                            NoteCard(
                                note = note,
                                enabled = enabled,
                                onTogglePin = { viewModel.setPinned(note.id, !note.pinned) },
                                onEdit = {
                                    editorNote = note
                                    editorVisible = true
                                },
                                onDelete = { noteToDelete = note },
                            )
                        }
                    }
                }
            }
        }
    }

    if (editorVisible) {
        NoteEditor(
            note = editorNote,
            saving = state.isSaving,
            onDismiss = { editorVisible = false },
            onSave = { title, body ->
                editorNote?.let { viewModel.updateNote(it.id, title, body) }
                    ?: viewModel.addNote(title, body)
                editorVisible = false
            },
        )
    }
    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text(stringResource(R.string.notes_delete_title)) },
            text = { Text(stringResource(R.string.notes_delete_body, note.title)) },
            confirmButton = {
                TextButton(enabled = enabled, onClick = {
                    viewModel.deleteNote(note.id)
                    noteToDelete = null
                }) { Text(stringResource(R.string.notes_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun NoteCard(note: Note, enabled: Boolean, onTogglePin: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
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
                IconButton(onClick = { menu = true }, enabled = enabled) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
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
}

@Composable
private fun NoteEditor(note: Note?, saving: Boolean, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var title by remember(note?.id) { mutableStateOf(note?.title.orEmpty()) }
    var body by remember(note?.id) { mutableStateOf(note?.body.orEmpty()) }
    val validation = NoteValidation.validate(title, body)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (note == null) R.string.notes_add_title else R.string.notes_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.notes_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.notes_body_label)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                validation?.let {
                    Text(stringResource(it.stringRes()), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = validation == null && !saving,
                onClick = { onSave(title, body) },
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NoteFeedbackBanner(message: NoteUiMessage, onDismiss: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(Modifier.padding(start = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(message.stringRes()), Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        }
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
    NoteUiMessage.NoteAdded -> R.string.notes_notice_added
    NoteUiMessage.NoteUpdated -> R.string.notes_notice_updated
    NoteUiMessage.NoteDeleted -> R.string.notes_notice_deleted
    NoteUiMessage.NotePinned -> R.string.notes_notice_pinned
    NoteUiMessage.NoteUnpinned -> R.string.notes_notice_unpinned
}
