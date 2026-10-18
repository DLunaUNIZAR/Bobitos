package com.dlunaunizar.bobitos.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.dlunaunizar.bobitos.core.designsystem.component.CreatedUndoEffect
import com.dlunaunizar.bobitos.core.designsystem.component.EditorSaveEffect
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.FeedbackSnackbarEffect
import com.dlunaunizar.bobitos.core.designsystem.component.GroupedRow
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
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.data.repository.LoadedImage
import com.dlunaunizar.bobitos.feature.common.CatalogGroup
import com.dlunaunizar.bobitos.feature.common.CatalogHeader
import com.dlunaunizar.bobitos.feature.common.CatalogLayout
import com.dlunaunizar.bobitos.feature.common.groupCatalog

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
    // Un ejercicio propio recién borrado, para ofrecer «Deshacer»; una ficha común borrada por el admin avisa desde el ViewModel.
    var undoable by remember { mutableStateOf<CatalogExercise?>(null) }
    val requestDelete: (CatalogExercise) -> Unit = { exercise ->
        if (exercise.canUndoDelete(state.currentUid)) {
            viewModel.deleteExercise(exercise.id) { undoable = exercise }
        } else {
            deleteTarget = exercise
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf<ExerciseType?>(null) }
    var groupFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = ExerciseFilter(query, typeFilter, groupFilter)
    val closeEditor: () -> Unit = {
        // Cerrar tras un guardado fallido descarta ese error (ya se veía en la hoja).
        state.editorSave.discardFailure(viewModel::clearFeedback)
        editorOpen = false
    }

    val listState = rememberLazyListState()
    ScreenScaffold(
        module = AppModule.SPORT,
        title = stringResource(R.string.exercises_title),
        onBack = onBack,
        modifier = modifier,
        floatingActionButton = {
            val fabExpanded = rememberFabExpanded(listState)
            ExtendedFloatingActionButton(
                expanded = fabExpanded,
                onClick = {
                    editorExerciseId = null
                    editorOpen = true
                },
                icon = {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = if (fabExpanded) null else stringResource(R.string.exercises_add),
                    )
                },
                text = { Text(stringResource(R.string.exercises_add)) },
            )
        },
    ) { padding ->
        val snackbar = LocalSnackbarHostState.current
        val scope = rememberCoroutineScope()
        val deletedMessage = stringResource(R.string.exercises_notice_deleted)
        val undoLabel = stringResource(R.string.undo)
        LaunchedEffect(undoable) {
            val exercise = undoable ?: return@LaunchedEffect
            undoable = null
            scope.launchUndo(snackbar, deletedMessage, undoLabel) { viewModel.restoreExercise(exercise) }
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            FeedbackSnackbarEffect(
                error = state.error,
                notice = state.notice,
                editorSave = state.editorSave,
                editorOpen = editorOpen,
                onShown = viewModel::clearFeedback,
            ) { stringResource(it.stringResourceId) }
            CreatedUndoEffect(state.created, viewModel::consumeCreated) { viewModel.undoCreate(it.id) }
            ExerciseCatalog(
                state = state,
                filter = filter,
                onQueryChange = { query = it },
                onTypeChange = { typeFilter = it },
                onGroupChange = { groupFilter = it },
                loadImage = viewModel::loadImage,
                onRetry = viewModel::retryCatalog,
                onOpen = {
                    detailExerciseId = it.id
                    detailOpen = true
                },
                onEdit = {
                    editorExerciseId = it.id
                    editorOpen = true
                },
                onDelete = requestDelete,
                deleteEnabled = !state.isSaving,
                listState = listState,
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
                requestDelete(exercise)
            },
            onDismiss = { detailOpen = false },
            deleteEnabled = !state.isSaving,
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
        onGone = closeEditor,
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
            onDismiss = closeEditor,
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
                viewModel.deleteExercise(exercise.id, undoable = false)
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
    onQueryChange: (String) -> Unit,
    onTypeChange: (ExerciseType?) -> Unit,
    onGroupChange: (String?) -> Unit,
    loadImage: suspend (CatalogExercise) -> LoadedImage?,
    onOpen: (CatalogExercise) -> Unit,
    onEdit: (CatalogExercise) -> Unit,
    onDelete: (CatalogExercise) -> Unit,
    deleteEnabled: Boolean,
    onRetry: () -> Unit,
    listState: LazyListState,
) {
    val all = (state.catalog as? UiState.Content)?.value.orEmpty()
    // Búsqueda y desplegables en la franja teñida: en la lista es su primer elemento y se va con ella al desplazar.
    val filterBand: @Composable () -> Unit = {
        ExerciseFilterBand(filter, all, onQueryChange, onTypeChange, onGroupChange)
    }
    when (val catalog = state.catalog) {
        UiState.Loading -> {
            filterBand()
            LoadingState(Modifier.fillMaxWidth())
        }
        is UiState.Error -> {
            filterBand()
            ErrorState(
                Modifier.fillMaxWidth(),
                message =
                state.catalogError?.let { stringResource(it.stringResourceId) }
                    ?: stringResource(catalog.failure.messageRes()),
                onRetry = onRetry,
            )
        }
        is UiState.Content -> {
            val filtered = remember(catalog.value, filter) { catalog.value.filterExercises(filter) }
            // Al buscar, lista plana con el grupo muscular en la línea de cada ejercicio.
            val layout = if (filter.query.isNotBlank()) CatalogLayout.FLAT else CatalogLayout.BY_CATEGORY
            val groups = remember(filtered, layout, filter.muscleGroup) {
                groupCatalog(
                    items = filtered,
                    isMine = { false },
                    categoryOf = CatalogExercise::muscleGroup,
                    layout = layout,
                    selectedCategory = filter.muscleGroup,
                )
            }
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = Spacing.fabClearance)) {
                item(key = "filters") { filterBand() }
                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                            icon = Icons.Rounded.FitnessCenter,
                            title = stringResource(
                                if (filter.isActive && catalog.value.isNotEmpty()) {
                                    R.string.exercises_no_results
                                } else {
                                    R.string.exercises_empty
                                },
                            ),
                        )
                    }
                }
                exerciseGroups(groups, state, loadImage, onOpen, onEdit, onDelete, deleteEnabled)
            }
        }
    }
}

@Suppress("LongParameterList")
private fun LazyListScope.exerciseGroups(
    groups: List<CatalogGroup<CatalogExercise>>,
    state: ExercisesUiState,
    loadImage: suspend (CatalogExercise) -> LoadedImage?,
    onOpen: (CatalogExercise) -> Unit,
    onEdit: (CatalogExercise) -> Unit,
    onDelete: (CatalogExercise) -> Unit,
    deleteEnabled: Boolean,
) {
    groups.forEachIndexed { groupIndex, group ->
        val header = group.header
        if (header != null) {
            stickyHeader(key = "header-$groupIndex", contentType = "header") {
                SectionHeader(
                    title = header.title(),
                    count = group.items.size,
                    sticky = true,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }
        itemsIndexed(group.items, key = { _, exercise -> exercise.id }) { index, exercise ->
            ExerciseRow(
                exercise = exercise,
                position = rowPosition(index, group.items.size),
                canEdit = state.canEdit(exercise),
                load = { loadImage(exercise) },
                onOpen = { onOpen(exercise) },
                onEdit = { onEdit(exercise) },
                onDelete = { onDelete(exercise) },
                deleteEnabled = deleteEnabled,
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = if (index == group.items.size - 1) Spacing.md else 0.dp)
                    .animateItem(),
            )
        }
    }
}

@Composable
private fun CatalogHeader.title(): String = when (this) {
    CatalogHeader.Mine -> ""
    is CatalogHeader.Category -> name
    CatalogHeader.Other -> stringResource(R.string.exercises_group_other)
}

@Composable
private fun ExerciseFilterBand(
    filter: ExerciseFilter,
    exercises: List<CatalogExercise>,
    onQueryChange: (String) -> Unit,
    onTypeChange: (ExerciseType?) -> Unit,
    onGroupChange: (String?) -> Unit,
) {
    val groups = remember(exercises) { exercises.muscleGroups() }
    ModuleHeaderBand {
        Column(
            Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SearchField(
                query = filter.query,
                onQueryChange = onQueryChange,
                visible = true,
                label = stringResource(R.string.exercises_search_label),
            )
            if (exercises.isNotEmpty()) {
                ExerciseFilterMenus(
                    groups = groups,
                    filter = filter,
                    onTypeChange = onTypeChange,
                    onGroupChange = onGroupChange,
                )
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: CatalogExercise,
    position: RowPosition,
    canEdit: Boolean,
    load: suspend () -> LoadedImage?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    deleteEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val supporting = exerciseSupportingRes(exercise).map { stringResource(it) }.joinToString(" · ")
    GroupedRow(position = position, onClick = onOpen, modifier = modifier) {
        ExerciseThumbnail(exercise = exercise, load = load)
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canEdit) {
            Box {
                MoreOptionsButton(itemName = exercise.name, onClick = { menu = true })
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
