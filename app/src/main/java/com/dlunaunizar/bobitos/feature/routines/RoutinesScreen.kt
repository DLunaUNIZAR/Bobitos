package com.dlunaunizar.bobitos.feature.routines

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.feature.exercises.isStrength
import com.dlunaunizar.bobitos.feature.exercises.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onBack: () -> Unit,
    canWrite: Boolean,
    modifier: Modifier = Modifier,
    viewModel: RoutinesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        viewModel.observe()
        onDispose { viewModel.stopObserving() }
    }
    var detail by remember { mutableStateOf<Routine?>(null) }
    var editor by remember { mutableStateOf<RoutineEditorRequest?>(null) }
    var deleteTarget by remember { mutableStateOf<Routine?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { viewModel.setQuery(query) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routines_title)) },
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
            if (canWrite) {
                ExtendedFloatingActionButton(
                    onClick = { editor = RoutineEditorRequest(null) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.routines_add)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.lg)) {
            state.error?.let { RoutinesFeedback(it, isError = true, onDismiss = viewModel::clearFeedback) }
            state.notice?.let { RoutinesFeedback(it, isError = false, onDismiss = viewModel::clearFeedback) }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.routines_search_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            )
            RoutinesCatalog(
                state = state,
                canWrite = canWrite,
                onOpen = { detail = it },
                onEdit = { editor = RoutineEditorRequest(it) },
                onDelete = { deleteTarget = it },
            )
        }
    }

    detail?.let { RoutineDetailDialog(routine = it, onDismiss = { detail = null }) }
    editor?.let { request ->
        RoutineEditor(
            request = request,
            catalog = state.exercises,
            isAdmin = state.isAdmin,
            saving = state.isSaving,
            canWrite = canWrite,
            onDismiss = { editor = null },
            onSave = { visibility, title, description, exercises ->
                request.routine?.let { viewModel.updateRoutine(it.id, title, description, exercises) }
                    ?: viewModel.createRoutine(visibility, title, description, exercises)
                editor = null
            },
        )
    }
    deleteTarget?.let { routine ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.routines_delete_title)) },
            text = { Text(stringResource(R.string.routines_delete_body, routine.title)) },
            confirmButton = {
                TextButton(enabled = canWrite && !state.isSaving, onClick = {
                    viewModel.deleteRoutine(routine.id)
                    deleteTarget = null
                }) { Text(stringResource(R.string.routines_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun RoutinesCatalog(
    state: RoutinesUiState,
    canWrite: Boolean,
    onOpen: (Routine) -> Unit,
    onEdit: (Routine) -> Unit,
    onDelete: (Routine) -> Unit,
) {
    // Las comunes ya publicadas por el propio usuario aparecen en «Mis rutinas»; se excluyen de aquí.
    val mineIds = (state.mine as? UiState.Content)?.value.orEmpty().map(Routine::id).toSet()
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        routinesSection(
            titleRes = R.string.routines_mine,
            routines = state.mine,
            query = state.query,
            canEdit = canWrite,
            excludeIds = emptySet(),
            onOpen = onOpen,
            onEdit = onEdit,
            onDelete = onDelete,
        )
        routinesSection(
            titleRes = R.string.routines_common,
            routines = state.global,
            query = state.query,
            canEdit = canWrite && state.isAdmin,
            excludeIds = mineIds,
            onOpen = onOpen,
            onEdit = onEdit,
            onDelete = onDelete,
        )
    }
}

private fun LazyListScope.routinesSection(
    @StringRes titleRes: Int,
    routines: UiState<List<Routine>>,
    query: String,
    canEdit: Boolean,
    excludeIds: Set<String>,
    onOpen: (Routine) -> Unit,
    onEdit: (Routine) -> Unit,
    onDelete: (Routine) -> Unit,
) {
    item(key = "header-$titleRes") {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
    when (routines) {
        UiState.Loading -> item(key = "loading-$titleRes") { LoadingState(Modifier.fillMaxWidth()) }
        is UiState.Error -> item(key = "error-$titleRes") {
            ErrorState(Modifier.fillMaxWidth(), message = routines.message)
        }
        is UiState.Content -> {
            val visible = routines.value.filter { it.id !in excludeIds }
            val filtered = visible.filter { it.matches(query) }
            if (filtered.isEmpty()) {
                val noResults = query.isNotBlank() && visible.isNotEmpty()
                item(key = "empty-$titleRes") {
                    EmptyState(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.FitnessCenter,
                        title = stringResource(
                            if (noResults) R.string.routines_no_results else R.string.routines_empty,
                        ),
                    )
                }
            } else {
                items(filtered, key = { "$titleRes-${it.id}" }) { routine ->
                    RoutineCard(
                        routine = routine,
                        canEdit = canEdit,
                        onOpen = { onOpen(routine) },
                        onEdit = { onEdit(routine) },
                        onDelete = { onDelete(routine) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    canEdit: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = Spacing.md,
                top = Spacing.sm,
                end = Spacing.xs,
                bottom = Spacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    routine.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.routines_exercise_count, routine.exercises.orEmpty().size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canEdit) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.routines_edit)) },
                            onClick = {
                                menu = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.routines_delete)) },
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
}

@Composable
private fun RoutineDetailDialog(routine: Routine, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(routine.title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                routine.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                val exercises = routine.exercises.orEmpty()
                if (exercises.isEmpty()) {
                    Text(
                        stringResource(R.string.routines_no_exercises),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    exercises.forEach { exercise ->
                        Text("• ${exercise.name}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = exercise.summary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        exercise.notes?.let { notes ->
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun RoutinesFeedback(message: RoutineUiMessage, isError: Boolean, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(Modifier.padding(start = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(message.stringResourceId), Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        }
    }
}

@Composable
private fun RoutineExercise.summary(): String {
    val label = stringResource(type.labelRes)
    return if (type.isStrength) {
        val sets = sets.size
        stringResource(R.string.routines_summary_strength, label, sets)
    } else {
        val parts = listOfNotNull(
            durationMinutes?.let { stringResource(R.string.routines_summary_minutes, it) },
            level?.takeIf(String::isNotBlank),
        ).joinToString(" · ")
        if (parts.isBlank()) label else "$label · $parts"
    }
}

private fun Routine.matches(query: String): Boolean {
    if (query.isBlank()) return true
    return title.contains(query.trim(), ignoreCase = true)
}

private val RoutineUiMessage.stringResourceId: Int
    get() = when (this) {
        RoutineUiMessage.TitleRequired -> R.string.routines_error_title_required
        RoutineUiMessage.TitleTooLong -> R.string.routines_error_title_too_long
        RoutineUiMessage.DescriptionTooLong -> R.string.routines_error_description_too_long
        RoutineUiMessage.NotAuthenticated -> R.string.space_error_not_authenticated
        RoutineUiMessage.EmailNotVerified -> R.string.space_error_email_not_verified
        RoutineUiMessage.NotFound -> R.string.routines_error_not_found
        RoutineUiMessage.PermissionDenied -> R.string.space_error_permission_denied
        RoutineUiMessage.NetworkError -> R.string.space_error_network
        RoutineUiMessage.UnexpectedError -> R.string.space_error_unexpected
        RoutineUiMessage.Saved -> R.string.routines_notice_saved
        RoutineUiMessage.Deleted -> R.string.routines_notice_deleted
    }
