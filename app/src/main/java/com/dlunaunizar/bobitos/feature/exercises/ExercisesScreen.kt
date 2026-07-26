package com.dlunaunizar.bobitos.feature.exercises

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExercisesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        viewModel.observe()
        onDispose { viewModel.stopObserving() }
    }
    var showNew by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CatalogExercise?>(null) }
    var deleteTarget by remember { mutableStateOf<CatalogExercise?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { viewModel.setQuery(query) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exercises_title)) },
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
            ExtendedFloatingActionButton(
                onClick = { showNew = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.exercises_add)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.lg)) {
            state.error?.let { ExercisesFeedback(it, isError = true, onDismiss = viewModel::clearFeedback) }
            state.notice?.let { ExercisesFeedback(it, isError = false, onDismiss = viewModel::clearFeedback) }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.exercises_search_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            )
            ExerciseCatalog(
                state = state,
                onEdit = { editing = it },
                onDelete = { deleteTarget = it },
            )
        }
    }

    if (showNew) {
        ExerciseEditorDialog(
            exercise = null,
            saving = state.isSaving,
            onDismiss = { showNew = false },
            onSave = { name, type, muscle ->
                viewModel.createExercise(name, type, muscle)
                showNew = false
            },
        )
    }
    editing?.let { exercise ->
        ExerciseEditorDialog(
            exercise = exercise,
            saving = state.isSaving,
            onDismiss = { editing = null },
            onSave = { name, type, muscle ->
                viewModel.updateExercise(exercise.id, name, type, muscle)
                editing = null
            },
        )
    }
    deleteTarget?.let { exercise ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.exercises_delete_title)) },
            text = { Text(stringResource(R.string.exercises_delete_body, exercise.name)) },
            confirmButton = {
                TextButton(enabled = !state.isSaving, onClick = {
                    viewModel.deleteExercise(exercise.id)
                    deleteTarget = null
                }) { Text(stringResource(R.string.exercises_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ExerciseCatalog(
    state: ExercisesUiState,
    onEdit: (CatalogExercise) -> Unit,
    onDelete: (CatalogExercise) -> Unit,
) {
    when (val catalog = state.catalog) {
        UiState.Loading -> LoadingState(Modifier.fillMaxWidth())
        is UiState.Error -> ErrorState(Modifier.fillMaxWidth(), message = catalog.message)
        is UiState.Content -> {
            val filtered = catalog.value.filter { it.matches(state.query) }
            if (filtered.isEmpty()) {
                EmptyState(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.FitnessCenter,
                    title = stringResource(
                        if (state.query.isNotBlank() && catalog.value.isNotEmpty()) {
                            R.string.exercises_no_results
                        } else {
                            R.string.exercises_empty
                        },
                    ),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(filtered, key = CatalogExercise::id) { exercise ->
                        ExerciseRow(
                            exercise = exercise,
                            canEdit = state.canEdit(exercise),
                            onEdit = { onEdit(exercise) },
                            onDelete = { onDelete(exercise) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: CatalogExercise,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = Spacing.md,
                top = Spacing.sm,
                end = Spacing.xs,
                bottom = Spacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                exercise.muscleGroup?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(stringResource(exercise.type.labelRes)) },
                colors = AssistChipDefaults.assistChipColors(
                    disabledLabelColor = exercise.type.accent(),
                ),
            )
            if (canEdit) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.exercises_edit)) },
                            onClick = {
                                menu = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.exercises_delete)) },
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
private fun ExerciseEditorDialog(
    exercise: CatalogExercise?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, ExerciseType, String?) -> Unit,
) {
    var name by remember(exercise?.id) { mutableStateOf(exercise?.name.orEmpty()) }
    var type by remember(exercise?.id) { mutableStateOf(exercise?.type ?: ExerciseType.MAQUINA) }
    var muscle by remember(exercise?.id) { mutableStateOf(exercise?.muscleGroup.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (exercise == null) R.string.exercises_add_title else R.string.exercises_edit_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.exercises_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.exercises_type_label), style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    ExerciseType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(stringResource(option.labelRes)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = muscle,
                    onValueChange = { muscle = it },
                    label = { Text(stringResource(R.string.exercises_muscle_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !saving,
                onClick = { onSave(name, type, muscle.trim().ifBlank { null }) },
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ExercisesFeedback(message: ExerciseUiMessage, isError: Boolean, onDismiss: () -> Unit) {
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

private fun CatalogExercise.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val trimmed = query.trim()
    return name.contains(trimmed, ignoreCase = true) || muscleGroup?.contains(trimmed, ignoreCase = true) == true
}

private val ExerciseUiMessage.stringResourceId: Int
    get() = when (this) {
        ExerciseUiMessage.NameRequired -> R.string.exercises_error_name_required
        ExerciseUiMessage.NameTooLong -> R.string.exercises_error_name_too_long
        ExerciseUiMessage.MuscleGroupTooLong -> R.string.exercises_error_muscle_too_long
        ExerciseUiMessage.AlreadyExists -> R.string.exercises_error_exists
        ExerciseUiMessage.NotAuthenticated -> R.string.space_error_not_authenticated
        ExerciseUiMessage.EmailNotVerified -> R.string.space_error_email_not_verified
        ExerciseUiMessage.NotFound -> R.string.exercises_error_not_found
        ExerciseUiMessage.PermissionDenied -> R.string.space_error_permission_denied
        ExerciseUiMessage.NetworkError -> R.string.space_error_network
        ExerciseUiMessage.UnexpectedError -> R.string.space_error_unexpected
        ExerciseUiMessage.Saved -> R.string.exercises_notice_saved
        ExerciseUiMessage.Deleted -> R.string.exercises_notice_deleted
    }
