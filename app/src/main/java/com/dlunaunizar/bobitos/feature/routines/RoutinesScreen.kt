package com.dlunaunizar.bobitos.feature.routines

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.messageRes
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosDialog
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosInfoSheet
import com.dlunaunizar.bobitos.core.designsystem.component.CreatedUndoEffect
import com.dlunaunizar.bobitos.core.designsystem.component.DeclareHeaderBand
import com.dlunaunizar.bobitos.core.designsystem.component.EditorSaveEffect
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.FeedbackSnackbarEffect
import com.dlunaunizar.bobitos.core.designsystem.component.GroupedRow
import com.dlunaunizar.bobitos.core.designsystem.component.InfoSheetAction
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.ModuleHeaderBand
import com.dlunaunizar.bobitos.core.designsystem.component.MoreOptionsButton
import com.dlunaunizar.bobitos.core.designsystem.component.RowPosition
import com.dlunaunizar.bobitos.core.designsystem.component.ScreenScaffold
import com.dlunaunizar.bobitos.core.designsystem.component.SearchField
import com.dlunaunizar.bobitos.core.designsystem.component.SectionHeader
import com.dlunaunizar.bobitos.core.designsystem.component.discardFailure
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.designsystem.component.rememberEditorSlot
import com.dlunaunizar.bobitos.core.designsystem.component.rowPosition
import com.dlunaunizar.bobitos.core.designsystem.rememberFabExpanded
import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
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
    // Editor y detalle sobreviven a una rotación: se guarda el id de la rutina, no el objeto.
    var detailRoutineId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorRoutineId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Routine?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { viewModel.setQuery(query) }
    val closeEditor = {
        // Cerrar tras un guardado fallido descarta ese error (ya se veía en la hoja).
        state.editorSave.discardFailure(viewModel::clearFeedback)
        editorOpen = false
        editorRoutineId = null
    }

    val listState = rememberLazyListState()
    ScreenScaffold(
        module = AppModule.SPORT,
        title = stringResource(R.string.routines_title),
        onBack = onBack,
        modifier = modifier,
        floatingActionButton = {
            if (canWrite) {
                val fabExpanded = rememberFabExpanded(listState)
                ExtendedFloatingActionButton(
                    expanded = fabExpanded,
                    onClick = {
                        editorRoutineId = null
                        editorOpen = true
                    },
                    icon = {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = if (fabExpanded) null else stringResource(R.string.routines_add),
                        )
                    },
                    text = { Text(stringResource(R.string.routines_add)) },
                )
            }
        },
    ) { padding ->
        val snackbar = LocalSnackbarHostState.current
        val scope = rememberCoroutineScope()
        val deletedMessage = stringResource(R.string.routines_notice_deleted)
        val undoLabel = stringResource(R.string.undo)
        Column(Modifier.fillMaxSize().padding(padding)) {
            FeedbackSnackbarEffect(
                error = state.error,
                notice = state.notice,
                editorSave = state.editorSave,
                editorOpen = editorOpen,
                onShown = viewModel::clearFeedback,
            ) { stringResource(it.stringResourceId) }
            CreatedUndoEffect(state.created, viewModel::consumeCreated) { viewModel.undoCreate(it.id) }
            RoutinesCatalog(
                state = state,
                query = query,
                onQueryChange = { query = it },
                canWrite = canWrite,
                deleteEnabled = !state.isSaving,
                onOpen = { detailRoutineId = it.id },
                onEdit = {
                    editorRoutineId = it.id
                    editorOpen = true
                },
                onDelete = { routine ->
                    if (routine.canUndoDelete()) {
                        viewModel.deleteRoutine(routine.id) {
                            scope.launchUndo(snackbar, deletedMessage, undoLabel) { viewModel.restoreRoutine(routine) }
                        }
                    } else {
                        deleteTarget = routine
                    }
                },
                onRetry = viewModel::retry,
                listState = listState,
            )
        }
    }

    EditorSaveEffect(
        status = state.editorSave,
        editorOpen = editorOpen,
        onClose = closeEditor,
        onConsume = viewModel::consumeEditorSave,
    )
    RoutineSheetsHost(
        state = state,
        canWrite = canWrite,
        onCatalogNeeded = viewModel::observeExerciseCatalog,
        detailRoutineId = detailRoutineId,
        editorOpen = editorOpen,
        editorRoutineId = editorRoutineId,
        onCloseDetail = { detailRoutineId = null },
        onFork = { routine ->
            viewModel.fork(routine)
            detailRoutineId = null
        },
        onCloseEditor = closeEditor,
        onSave = { routine, visibility, title, description, exercises ->
            routine?.let { viewModel.updateRoutine(it.id, title, description, exercises) }
                ?: viewModel.createRoutine(visibility, title, description, exercises)
        },
    )
    deleteTarget?.let { routine ->
        BobitosDialog(
            title = stringResource(R.string.routines_delete_title),
            message = stringResource(R.string.routines_delete_body, routine.title),
            confirmLabel = stringResource(R.string.routines_delete),
            destructive = true,
            confirmEnabled = canWrite && !state.isSaving,
            onConfirm = {
                viewModel.deleteRoutine(routine.id, undoable = false)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun RoutinesCatalog(
    state: RoutinesUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    canWrite: Boolean,
    deleteEnabled: Boolean,
    onOpen: (Routine) -> Unit,
    onEdit: (Routine) -> Unit,
    onDelete: (Routine) -> Unit,
    onRetry: () -> Unit,
    listState: LazyListState,
) {
    // La franja va dentro de la lista y sale de composición al desplazar: se declara aquí, fuera.
    DeclareHeaderBand()
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = Spacing.fabClearance)) {
        // Búsqueda en la franja teñida: primer elemento de la lista, se va con ella al desplazar.
        item(key = "filters") {
            ModuleHeaderBand {
                SearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    visible = true,
                    label = stringResource(R.string.routines_search_label),
                    modifier = Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.md),
                )
            }
        }
        routinesSection(
            titleRes = R.string.routines_mine,
            routines = state.mine,
            query = state.query,
            canEdit = canWrite,
            deleteEnabled = deleteEnabled,
            onOpen = onOpen,
            onEdit = onEdit,
            onDelete = onDelete,
            onRetry = onRetry,
        )
        routinesSection(
            titleRes = R.string.routines_common,
            routines = state.global,
            query = state.query,
            canEdit = canWrite && state.isAdmin,
            deleteEnabled = deleteEnabled,
            onOpen = onOpen,
            onEdit = onEdit,
            onDelete = onDelete,
            onRetry = onRetry,
        )
    }
}

private fun LazyListScope.routinesSection(
    @StringRes titleRes: Int,
    routines: UiState<List<Routine>>,
    query: String,
    canEdit: Boolean,
    deleteEnabled: Boolean,
    onOpen: (Routine) -> Unit,
    onEdit: (Routine) -> Unit,
    onDelete: (Routine) -> Unit,
    onRetry: () -> Unit,
) {
    item(key = "header-$titleRes") {
        SectionHeader(title = stringResource(titleRes), modifier = Modifier.padding(horizontal = Spacing.lg))
    }
    when (routines) {
        UiState.Loading -> item(key = "loading-$titleRes") { LoadingState(Modifier.fillMaxWidth()) }
        is UiState.Error -> item(key = "error-$titleRes") {
            ErrorState(
                Modifier.fillMaxWidth(),
                message = stringResource(routines.failure.messageRes()),
                onRetry = onRetry,
            )
        }
        is UiState.Content -> {
            val visible = routines.value
            val filtered = visible.filter { it.matches(query) }
            if (filtered.isEmpty()) {
                val noResults = query.isNotBlank() && visible.isNotEmpty()
                item(key = "empty-$titleRes") {
                    EmptyState(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                        icon = Icons.Rounded.FitnessCenter,
                        title = stringResource(
                            if (noResults) R.string.routines_no_results else R.string.routines_empty,
                        ),
                    )
                }
            } else {
                itemsIndexed(filtered, key = { _, routine -> "$titleRes-${routine.id}" }) { index, routine ->
                    RoutineCard(
                        routine = routine,
                        position = rowPosition(index, filtered.size),
                        modifier = Modifier
                            .padding(horizontal = Spacing.lg)
                            .padding(bottom = if (index == filtered.size - 1) Spacing.md else 0.dp)
                            .animateItem(),
                        canEdit = canEdit,
                        deleteEnabled = deleteEnabled,
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
internal fun RoutineCard(
    routine: Routine,
    position: RowPosition,
    canEdit: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    // Falso mientras otra escritura está en curso: borrar sería un toque que no hace nada.
    deleteEnabled: Boolean = true,
) {
    var menu by remember { mutableStateOf(false) }
    GroupedRow(position = position, onClick = onOpen, modifier = modifier) {
        Column(Modifier.weight(1f)) {
            Text(
                routine.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.routines_exercise_count,
                    routine.exercises.orEmpty().size,
                    routine.exercises.orEmpty().size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canEdit) {
            Box {
                MoreOptionsButton(itemName = routine.title, onClick = { menu = true })
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
                        enabled = deleteEnabled,
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

// Muestra el detalle y el editor de rutina. Si la rutina abierta ya no existe, los cierra (mientras las
// listas cargan, espera) y mantiene la rutina durante una recarga para no perder el borrador.
@Composable
private fun RoutineSheetsHost(
    state: RoutinesUiState,
    canWrite: Boolean,
    onCatalogNeeded: () -> Unit,
    detailRoutineId: String?,
    editorOpen: Boolean,
    editorRoutineId: String?,
    onCloseDetail: () -> Unit,
    onCloseEditor: () -> Unit,
    onFork: (Routine) -> Unit,
    onSave: (Routine?, RoutineVisibility, String, String?, List<RoutineExercise>) -> Unit,
) {
    val global = state.global as? UiState.Content
    val mine = state.mine as? UiState.Content
    // Las dos listas juntas; null mientras alguna carga.
    val all = if (global != null && mine != null) global.value + mine.value else null
    val detail = rememberEditorSlot(
        open = detailRoutineId != null,
        id = detailRoutineId,
        items = all,
        idOf = Routine::id,
        onGone = onCloseDetail,
    )?.item
    val editor = rememberEditorSlot(
        open = editorOpen,
        id = editorRoutineId,
        items = all,
        idOf = Routine::id,
        onGone = onCloseEditor,
    )
    detail?.let {
        RoutineDetailSheet(
            routine = it,
            canFork = canForkRoutine(it, canWrite),
            onFork = { onFork(it) },
            onDismiss = onCloseDetail,
        )
    }
    editor?.let { slot ->
        val editing = slot.item
        RoutineEditor(
            routine = editing,
            catalog = state.exercises,
            onCatalogNeeded = onCatalogNeeded,
            isAdmin = state.isAdmin,
            saving = state.isSaving,
            errorMessage = state.error?.takeIf { state.editorSave == EditorSaveStatus.FAILED }
                ?.let { stringResource(it.stringResourceId) },
            canWrite = canWrite,
            onDismiss = onCloseEditor,
            onSave = { visibility, title, description, exercises ->
                onSave(editing, visibility, title, description, exercises)
            },
        )
    }
}

@Composable
private fun RoutineDetailSheet(routine: Routine, canFork: Boolean, onFork: () -> Unit, onDismiss: () -> Unit) {
    BobitosInfoSheet(
        title = routine.title,
        onDismiss = onDismiss,
        primaryAction = if (canFork) InfoSheetAction(stringResource(R.string.routines_fork), onFork) else null,
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
}

@Composable
private fun RoutineExercise.summary(): String {
    val label = stringResource(type.labelRes)
    return if (type.isStrength) {
        val pattern = setsPattern()
        when {
            pattern == null -> pluralStringResource(R.plurals.routines_summary_strength, sets.size, label, sets.size)
            pattern.timed ->
                stringResource(R.string.routines_summary_strength_seconds, label, pattern.count, pattern.value)
            else -> stringResource(R.string.routines_summary_strength_reps, label, pattern.count, pattern.value)
        }
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
        RoutineUiMessage.SaveTimeout -> R.string.write_timeout
        RoutineUiMessage.Saved -> R.string.routines_notice_saved
        RoutineUiMessage.Deleted -> R.string.routines_notice_deleted
    }
