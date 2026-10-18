package com.dlunaunizar.bobitos.feature.sport

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
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
import com.dlunaunizar.bobitos.core.designsystem.component.AddFab
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.designsystem.component.CreatedUndoEffect
import com.dlunaunizar.bobitos.core.designsystem.component.DayTitleButton
import com.dlunaunizar.bobitos.core.designsystem.component.EditorSaveEffect
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.FeedbackSnackbarEffect
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.ModuleHeaderBand
import com.dlunaunizar.bobitos.core.designsystem.component.ModuleScaffold
import com.dlunaunizar.bobitos.core.designsystem.component.MoreOptionsButton
import com.dlunaunizar.bobitos.core.designsystem.component.WeekDayStrip
import com.dlunaunizar.bobitos.core.designsystem.component.cascadeIn
import com.dlunaunizar.bobitos.core.designsystem.component.discardFailure
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.designsystem.component.rememberEditorSlot
import com.dlunaunizar.bobitos.core.designsystem.rememberFabExpanded
import com.dlunaunizar.bobitos.core.designsystem.rememberToday
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.designsystem.theme.categoryCardColors
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SportActivity
import com.dlunaunizar.bobitos.core.model.SportType
import com.dlunaunizar.bobitos.feature.common.MemberCheckboxes
import com.dlunaunizar.bobitos.feature.exercises.ExerciseDraft
import com.dlunaunizar.bobitos.feature.exercises.ExerciseDraftListSaver
import com.dlunaunizar.bobitos.feature.exercises.ExerciseListEditor
import com.dlunaunizar.bobitos.feature.exercises.toExerciseDrafts
import com.dlunaunizar.bobitos.feature.exercises.toRoutineExercises
import com.dlunaunizar.bobitos.feature.routines.routineOriginRes
import java.time.LocalDate

@Composable
fun SportScreen(
    spaceId: String,
    canWrite: Boolean,
    modifier: Modifier = Modifier,
    initialDate: LocalDate? = null,
    viewModel: SportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Si viene de una entrada de «Hoy», queda enfocado ese día; si no, el que tuviera.
    LaunchedEffect(initialDate) { initialDate?.let(viewModel::selectDay) }
    DisposableEffect(spaceId) {
        viewModel.observe(spaceId)
        onDispose { viewModel.stopObserving() }
    }
    val members = (state.members as? UiState.Content)?.value.orEmpty()
    val actionsEnabled = canWrite && !state.isSaving
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.sport_undo_deleted)
    val undoLabel = stringResource(R.string.undo)
    // El editor sobrevive a una rotación: se guarda el id de la actividad, no el objeto.
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorActivityId by rememberSaveable { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    ModuleScaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (canWrite) {
                val fabExpanded = rememberFabExpanded(listState)
                AddFab(
                    label = R.string.sport_add,
                    expanded = fabExpanded,
                    onClick = {
                        editorActivityId = null
                        editorOpen = true
                    },
                )
            }
        },
    ) { padding ->
        val snackbar = LocalSnackbarHostState.current
        FeedbackSnackbarEffect(
            error = state.error,
            notice = state.notice,
            editorSave = state.editorSave,
            editorOpen = editorOpen,
            onShown = viewModel::clearFeedback,
        ) { stringResource(it.stringResourceId) }
        CreatedUndoEffect(state.created, viewModel::consumeCreated) { viewModel.undoCreate(it.id) }
        val deleteWithUndo: (SportActivity) -> Unit = { activity ->
            // El espacio se captura al borrar: «Deshacer» devuelve la actividad (y su evento) a su espacio.
            val deletedFrom = spaceId
            viewModel.deleteActivity(activity.id) { event ->
                scope.launchUndo(snackbar, deletedMessage, undoLabel) {
                    viewModel.restoreActivity(deletedFrom, activity, event)
                }
            }
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            // La tira queda fija bajo la barra: no se desplaza con la lista.
            ModuleHeaderBand {
                WeekDayStrip(
                    days = state.weekDays,
                    selected = state.focusedDate,
                    today = rememberToday(),
                    onSelect = viewModel::selectDay,
                    onPrevious = viewModel::previousWeek,
                    onNext = viewModel::nextWeek,
                    previousLabel = stringResource(R.string.sport_prev_week),
                    nextLabel = stringResource(R.string.sport_next_week),
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DayTitleButton(state.focusedDate, viewModel::selectDay, Modifier.weight(1f))
                }
                when (val activities = state.activities) {
                    UiState.Loading -> LoadingState(Modifier.weight(1f))
                    is UiState.Error -> ErrorState(
                        Modifier.weight(1f),
                        message = stringResource(activities.failure.messageRes()),
                        onRetry = { viewModel.observe(spaceId) },
                    )
                    is UiState.Content -> {
                        val dayActivities = activities.value.filter { it.date == state.focusedDate }
                        if (dayActivities.isEmpty()) {
                            EmptyState(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Rounded.FitnessCenter,
                                title = stringResource(R.string.sport_empty_day),
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                contentPadding = PaddingValues(bottom = Spacing.fabClearance),
                            ) {
                                itemsIndexed(dayActivities, key = { _, a -> a.id }) { index, activity ->
                                    // La cascada se repite al cambiar de día.
                                    Box(Modifier.cascadeIn(index, key = state.focusedDate)) {
                                        ActivityCard(
                                            activity = activity,
                                            enabled = actionsEnabled,
                                            canWrite = canWrite,
                                            onEdit = {
                                                editorActivityId = activity.id
                                                editorOpen = true
                                            },
                                            onDelete = { deleteWithUndo(activity) },
                                            onToggleDone = { viewModel.setDone(activity.id, !activity.done) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val closeEditor = {
        // Cerrar tras un guardado fallido descarta ese error (ya se veía en la hoja).
        state.editorSave.discardFailure(viewModel::clearFeedback)
        editorOpen = false
        editorActivityId = null
    }
    EditorSaveEffect(
        status = state.editorSave,
        editorOpen = editorOpen,
        onClose = closeEditor,
        onConsume = viewModel::consumeEditorSave,
    )
    ActivityEditorHost(
        open = editorOpen,
        activityId = editorActivityId,
        activities = state.activities,
        members = members,
        routines = state.routines,
        catalog = state.exercises,
        onCatalogNeeded = viewModel::observeExerciseCatalog,
        saving = state.isSaving,
        errorMessage = state.error?.takeIf { state.editorSave == EditorSaveStatus.FAILED }
            ?.let { stringResource(it.stringResourceId) },
        canWrite = canWrite,
        onClose = closeEditor,
        onSave = { activity, type, name, participantIds, routineId, session ->
            activity?.let {
                viewModel.updateActivity(it.id, it.date, type, name, participantIds, routineId, session)
            } ?: viewModel.addActivity(state.focusedDate, type, name, participantIds, routineId, session)
        },
    )
}

@Composable
private fun ActivityInfo(activity: SportActivity, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = activity.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = activity.participantNames.takeIf(List<String>::isNotEmpty)?.joinToString(", ")
                ?: stringResource(R.string.sport_no_participants),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (activity.type == SportType.GIMNASIO && activity.session.isNotEmpty()) {
            Text(
                text = pluralStringResource(
                    R.plurals.routines_exercise_count,
                    activity.session.size,
                    activity.session.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (activity.done) {
            Text(
                text = stringResource(R.string.sport_done_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
    }
}

@Composable
internal fun ActivityCard(
    activity: SportActivity,
    enabled: Boolean,
    canWrite: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleDone: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = categoryCardColors(activity.type.accent()),
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm, end = Spacing.xs, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = activity.type.icon,
                contentDescription = null,
                tint = activity.type.accent(),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(Spacing.md))
            ActivityInfo(activity, modifier = Modifier.weight(1f))
            if (canWrite) {
                Box {
                    MoreOptionsButton(itemName = activity.name, onClick = { menuExpanded = true }, enabled = enabled)
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (activity.done) R.string.sport_mark_pending else R.string.sport_mark_done,
                                    ),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleDone()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sport_edit)) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sport_delete)) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

// Muestra el editor de actividad (nueva o existente). Si la actividad que se editaba ya no existe, lo cierra
// (mientras la lista carga, espera) y la mantiene durante una recarga para no perder el borrador.
@Composable
private fun ActivityEditorHost(
    open: Boolean,
    activityId: String?,
    activities: UiState<List<SportActivity>>,
    members: List<SpaceMember>,
    routines: List<Routine>,
    catalog: List<CatalogExercise>,
    onCatalogNeeded: () -> Unit,
    saving: Boolean,
    errorMessage: String?,
    canWrite: Boolean,
    onClose: () -> Unit,
    onSave: (SportActivity?, SportType, String, List<String>, String?, List<RoutineExercise>) -> Unit,
) {
    val slot = rememberEditorSlot(
        open = open,
        id = activityId,
        items = (activities as? UiState.Content)?.value,
        idOf = SportActivity::id,
        onGone = onClose,
    ) ?: return
    val activity = slot.item
    ActivityEditor(
        activity = activity,
        members = members,
        routines = routines,
        catalog = catalog,
        onCatalogNeeded = onCatalogNeeded,
        saving = saving,
        errorMessage = errorMessage,
        canWrite = canWrite,
        onDismiss = onClose,
        onSave = { type, name, participantIds, routineId, session ->
            onSave(activity, type, name, participantIds, routineId, session)
        },
    )
}

// Fila desplazable de tipos de deporte.
@Composable
private fun SportTypeChips(selected: SportType, onSelect: (SportType) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SportType.entries.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                leadingIcon = { Icon(option.icon, contentDescription = null, tint = option.accent()) },
                label = { Text(stringResource(option.labelRes)) },
            )
        }
    }
}

@Composable
private fun ActivityEditor(
    activity: SportActivity?,
    members: List<SpaceMember>,
    routines: List<Routine>,
    catalog: List<CatalogExercise>,
    onCatalogNeeded: () -> Unit,
    saving: Boolean,
    errorMessage: String?,
    canWrite: Boolean,
    onDismiss: () -> Unit,
    onSave: (SportType, String, List<String>, String?, List<RoutineExercise>) -> Unit,
) {
    val initial = ActivityDraft.of(activity)
    val initialSession = remember(activity?.id) {
        activity?.session.orEmpty().toExerciseDrafts().toRoutineExercises()
    }
    // Borrador guardable: campos simples y sesión de gimnasio (con sus series), para sobrevivir a una rotación.
    var draft by rememberSaveable(activity?.id) { mutableStateOf(initial) }
    val session = rememberSaveable(activity?.id, saver = ExerciseDraftListSaver) {
        activity?.session.orEmpty().toExerciseDrafts().toMutableStateList()
    }
    var pickingRoutine by rememberSaveable { mutableStateOf(false) }
    val type = draft.type
    val typeLabel = stringResource(type.labelRes)

    BobitosFormSheet(
        title = stringResource(if (activity == null) R.string.sport_add_title else R.string.sport_edit_title),
        confirmLabel = stringResource(R.string.save),
        canSubmit = canWrite,
        saving = saving,
        dirty = { draft != initial || session.toRoutineExercises() != initialSession },
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = {
            val gym = type == SportType.GIMNASIO
            onSave(
                type,
                draft.name.ifBlank { typeLabel },
                draft.selectedIds,
                draft.routineId.takeIf { gym },
                if (gym) session.toRoutineExercises() else emptyList(),
            )
        },
    ) {
        SportTypeChips(selected = type, onSelect = { draft = draft.copy(type = it) })
        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it) },
            label = { Text(stringResource(R.string.sport_name_label)) },
            placeholder = { Text(typeLabel) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        MemberCheckboxes(
            label = stringResource(R.string.sport_participants_label),
            members = members,
            selectedIds = draft.selectedIds,
            onToggle = { userId, selected -> draft = draft.withParticipant(userId, selected) },
        )
        if (type == SportType.GIMNASIO) {
            GymSessionSection(
                routineTitle = routines.firstOrNull { it.id == draft.routineId }?.title,
                session = session,
                catalog = catalog,
                onCatalogNeeded = onCatalogNeeded,
                onPickRoutine = { pickingRoutine = true },
            )
        }
    }

    if (pickingRoutine) {
        RoutinePickerDialog(
            routines = routines,
            onDismiss = { pickingRoutine = false },
            onClear = {
                draft = draft.copy(routineId = null)
                session.clear()
                pickingRoutine = false
            },
            onPick = { routine ->
                draft = draft.copy(routineId = routine.id)
                session.clear()
                session.addAll(routine.exercises.orEmpty().toExerciseDrafts())
                pickingRoutine = false
            },
        )
    }
}

@Composable
private fun GymSessionSection(
    routineTitle: String?,
    session: SnapshotStateList<ExerciseDraft>,
    catalog: List<CatalogExercise>,
    onCatalogNeeded: () -> Unit,
    onPickRoutine: () -> Unit,
) {
    Text(stringResource(R.string.sport_session_label), style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = routineTitle ?: stringResource(R.string.sport_routine_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onPickRoutine) { Text(stringResource(R.string.sport_routine_pick)) }
    }
    ExerciseListEditor(drafts = session, catalog = catalog, onCatalogNeeded = onCatalogNeeded)
}

@Composable
private fun RoutinePickerDialog(
    routines: List<Routine>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onPick: (Routine) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sport_routine_pick)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth()) { Text(stringResource(R.string.sport_routine_clear)) }
                }
                if (routines.isEmpty()) {
                    Text(
                        stringResource(R.string.sport_no_routines),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    routines.forEach { routine ->
                        TextButton(onClick = { onPick(routine) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(routine.title)
                                Text(
                                    stringResource(routineOriginRes(routine)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val SportUiMessage.stringResourceId: Int
    get() = when (this) {
        SportUiMessage.NameRequired -> R.string.sport_error_name_required
        SportUiMessage.NameTooLong -> R.string.sport_error_name_too_long
        SportUiMessage.InvalidParticipants -> R.string.sport_error_invalid_participants
        SportUiMessage.NotAuthenticated -> R.string.space_error_not_authenticated
        SportUiMessage.EmailNotVerified -> R.string.space_error_email_not_verified
        SportUiMessage.SpaceNotFound -> R.string.space_error_not_found
        SportUiMessage.ActivityNotFound -> R.string.sport_error_not_found
        SportUiMessage.PermissionDenied -> R.string.space_error_permission_denied
        SportUiMessage.NetworkError -> R.string.space_error_network
        SportUiMessage.UnexpectedError -> R.string.space_error_unexpected
        SportUiMessage.SaveTimeout -> R.string.write_timeout
        SportUiMessage.ActivityAdded -> R.string.sport_notice_added
        SportUiMessage.ActivityUpdated -> R.string.sport_notice_updated
        SportUiMessage.ActivityDeleted -> R.string.sport_notice_deleted
        SportUiMessage.ActivityDone -> R.string.sport_notice_done
        SportUiMessage.ActivityPending -> R.string.sport_notice_pending
    }
