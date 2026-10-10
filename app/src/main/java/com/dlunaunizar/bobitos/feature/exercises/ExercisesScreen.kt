package com.dlunaunizar.bobitos.feature.exercises

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosDialog
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosTopBar
import com.dlunaunizar.bobitos.core.designsystem.component.EditorSaveEffect
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.InfoChip
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.component.rememberEditorSlot
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
    // El editor sobrevive a una rotación: se guarda el id del ejercicio, no el objeto.
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailOpen by rememberSaveable { mutableStateOf(false) }
    var detailExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<CatalogExercise?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf<ExerciseType?>(null) }
    var groupFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = ExerciseFilter(query, typeFilter, groupFilter)

    Scaffold(
        modifier = modifier,
        topBar = {
            BobitosTopBar(title = stringResource(R.string.exercises_title), onBack = onBack)
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editorExerciseId = null
                    editorOpen = true
                },
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
                filter = filter,
                onTypeChange = { typeFilter = it },
                onGroupChange = { groupFilter = it },
                onRetry = viewModel::retryCatalog,
                onOpen = {
                    detailExerciseId = it.id
                    detailOpen = true
                },
                onEdit = {
                    editorExerciseId = it.id
                    editorOpen = true
                },
                onDelete = { deleteTarget = it },
            )
        }
    }

    rememberEditorSlot(
        open = detailOpen,
        id = detailExerciseId,
        items = (state.catalog as? UiState.Content)?.value,
        idOf = CatalogExercise::id,
        onGone = { detailOpen = false },
    )?.item?.let { exercise ->
        ExerciseDetailSheet(
            exercise = exercise,
            loadImage = { viewModel.loadImage(exercise) },
            canEdit = state.canEdit(exercise),
            onEdit = {
                detailOpen = false
                editorExerciseId = exercise.id
                editorOpen = true
            },
            onDelete = {
                detailOpen = false
                deleteTarget = exercise
            },
            onDismiss = { detailOpen = false },
        )
    }
    EditorSaveEffect(
        status = state.editorSave,
        editorOpen = editorOpen,
        onClose = { editorOpen = false },
        onConsume = viewModel::consumeEditorSave,
    )
    rememberEditorSlot(
        open = editorOpen,
        id = editorExerciseId,
        items = (state.catalog as? UiState.Content)?.value,
        idOf = CatalogExercise::id,
        onGone = { editorOpen = false },
    )?.let { slot ->
        val editorExercise = slot.item
        ExerciseEditorSheet(
            exercise = editorExercise,
            otherIds = (state.catalog as? UiState.Content)?.value.orEmpty()
                .mapNotNull { it.id.takeIf { id -> id != editorExercise?.id } }.toSet(),
            saving = state.isSaving,
            saved = state.editorSave == EditorSaveStatus.SAVED,
            errorMessage = state.error?.takeIf { state.editorSave == EditorSaveStatus.FAILED }
                ?.let { stringResource(it.stringResourceId) },
            onDismiss = { editorOpen = false },
            onSave = { input ->
                editorExercise?.let { viewModel.updateExercise(it.id, input) }
                    ?: viewModel.createExercise(input)
            },
        )
    }
    deleteTarget?.let { exercise ->
        BobitosDialog(
            title = stringResource(R.string.exercises_delete_title),
            message = stringResource(R.string.exercises_delete_body, exercise.name),
            confirmLabel = stringResource(R.string.exercises_delete),
            destructive = true,
            confirmEnabled = !state.isSaving,
            onConfirm = {
                viewModel.deleteExercise(exercise.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun ExerciseCatalog(
    state: ExercisesUiState,
    filter: ExerciseFilter,
    onTypeChange: (ExerciseType?) -> Unit,
    onGroupChange: (String?) -> Unit,
    onOpen: (CatalogExercise) -> Unit,
    onEdit: (CatalogExercise) -> Unit,
    onDelete: (CatalogExercise) -> Unit,
    onRetry: () -> Unit,
) {
    when (val catalog = state.catalog) {
        UiState.Loading -> LoadingState(Modifier.fillMaxWidth())
        is UiState.Error -> ErrorState(
            Modifier.fillMaxWidth(),
            message = state.catalogError?.let { stringResource(it.stringResourceId) } ?: catalog.message,
            onRetry = onRetry,
        )
        is UiState.Content -> {
            val groups = remember(catalog.value) { catalog.value.muscleGroups() }
            val filtered = remember(catalog.value, filter) { catalog.value.filterExercises(filter) }
            if (catalog.value.isNotEmpty()) {
                ExerciseFilterChips(
                    groups = groups,
                    filter = filter,
                    onTypeChange = onTypeChange,
                    onGroupChange = onGroupChange,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            if (filtered.isEmpty()) {
                EmptyState(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.FitnessCenter,
                    title = stringResource(
                        if (filter.isActive && catalog.value.isNotEmpty()) {
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
                            onOpen = { onOpen(exercise) },
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
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
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
            InfoChip(stringResource(exercise.type.labelRes), contentColor = exercise.type.accent())
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

private val ExerciseUiMessage.stringResourceId: Int
    get() = when (this) {
        ExerciseUiMessage.NameRequired -> R.string.exercises_error_name_required
        ExerciseUiMessage.NameTooLong -> R.string.exercises_error_name_too_long
        ExerciseUiMessage.MuscleGroupTooLong -> R.string.exercises_error_muscle_too_long
        ExerciseUiMessage.DescriptionTooLong -> R.string.exercises_error_description_too_long
        ExerciseUiMessage.AlreadyExists -> R.string.exercises_error_exists
        ExerciseUiMessage.NotAuthenticated -> R.string.space_error_not_authenticated
        ExerciseUiMessage.EmailNotVerified -> R.string.space_error_email_not_verified
        ExerciseUiMessage.NotFound -> R.string.exercises_error_not_found
        ExerciseUiMessage.PermissionDenied -> R.string.space_error_permission_denied
        ExerciseUiMessage.NetworkError -> R.string.space_error_network
        ExerciseUiMessage.UnexpectedError -> R.string.space_error_unexpected
        ExerciseUiMessage.SaveTimeout -> R.string.write_timeout
        ExerciseUiMessage.CatalogUnavailable -> R.string.exercises_catalog_unavailable
        ExerciseUiMessage.Saved -> R.string.exercises_notice_saved
        ExerciseUiMessage.Deleted -> R.string.exercises_notice_deleted
    }
