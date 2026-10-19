package com.dlunaunizar.bobitos.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.BobitosDates
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.messageRes
import com.dlunaunizar.bobitos.core.designsystem.component.AddFab
import com.dlunaunizar.bobitos.core.designsystem.component.AppDatePickerDialog
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.designsystem.component.Celebration
import com.dlunaunizar.bobitos.core.designsystem.component.CollapsibleHeaderBand
import com.dlunaunizar.bobitos.core.designsystem.component.CreatedUndoEffect
import com.dlunaunizar.bobitos.core.designsystem.component.EditorSaveEffect
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.FilterDropdown
import com.dlunaunizar.bobitos.core.designsystem.component.FilterDropdownRow
import com.dlunaunizar.bobitos.core.designsystem.component.FilterOption
import com.dlunaunizar.bobitos.core.designsystem.component.GroupedRow
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.ModuleScaffold
import com.dlunaunizar.bobitos.core.designsystem.component.MoreOptionsButton
import com.dlunaunizar.bobitos.core.designsystem.component.RowPosition
import com.dlunaunizar.bobitos.core.designsystem.component.SaveFeedbackEffect
import com.dlunaunizar.bobitos.core.designsystem.component.SearchField
import com.dlunaunizar.bobitos.core.designsystem.component.SectionHeader
import com.dlunaunizar.bobitos.core.designsystem.component.SwipeAction
import com.dlunaunizar.bobitos.core.designsystem.component.SwipeActionsBox
import com.dlunaunizar.bobitos.core.designsystem.component.TopBarActions
import com.dlunaunizar.bobitos.core.designsystem.component.checkBounce
import com.dlunaunizar.bobitos.core.designsystem.component.completingExit
import com.dlunaunizar.bobitos.core.designsystem.component.errorSupportingText
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.designsystem.component.rememberCelebrationState
import com.dlunaunizar.bobitos.core.designsystem.component.rememberCompletingItems
import com.dlunaunizar.bobitos.core.designsystem.component.rememberEditorSlot
import com.dlunaunizar.bobitos.core.designsystem.component.rememberFormValidationState
import com.dlunaunizar.bobitos.core.designsystem.component.rowPosition
import com.dlunaunizar.bobitos.core.designsystem.rememberFabExpanded
import com.dlunaunizar.bobitos.core.designsystem.rememberToday
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.designsystem.theme.categoryRowTint
import com.dlunaunizar.bobitos.core.model.RecurrenceUnit
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskRecurrence
import com.dlunaunizar.bobitos.core.model.TaskStatus
import com.dlunaunizar.bobitos.core.model.TaskType
import com.dlunaunizar.bobitos.feature.spaces.pendingDueTodayOrEarlier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TasksScreen(
    spaceId: String,
    canWrite: Boolean,
    modifier: Modifier = Modifier,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(spaceId) {
        viewModel.observe(spaceId)
        onDispose { viewModel.stopObserving() }
    }
    val members = (state.members as? UiState.Content)?.value.orEmpty()
    val allTasks = (state.tasks as? UiState.Content)?.value.orEmpty()
    val visibleTasks = remember(allTasks, state.filters) { state.filters.apply(allTasks) }
    val enabled = canWrite && !state.save.isSaving
    // El editor sobrevive a una rotación: se guarda el id de la tarea (no el objeto) y el índice de la plantilla.
    var editorTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorTemplateIndex by rememberSaveable { mutableStateOf(-1) }
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    val openEditor: (taskId: String?, templateIndex: Int) -> Unit = { taskId, templateIndex ->
        editorTaskId = taskId
        editorTemplateIndex = templateIndex
        editorVisible = true
    }
    // Cerrar el editor tras un guardado fallido descarta ese error (ya se veía en la hoja).
    val closeEditor: () -> Unit = {
        viewModel.feedback.resetEditor()
        editorVisible = false
    }
    val editorTemplate = homeTaskTemplates.getOrNull(editorTemplateIndex)
    var templatesVisible by remember { mutableStateOf(false) }
    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val listState = rememberLazyListState()
    val completing = rememberCompletingItems()
    val today = rememberToday()
    val celebration = rememberCelebrationState(
        source = allTasks,
        pending = pendingDueTodayOrEarlier(allTasks, today, ZoneId.systemDefault()),
    )
    ModuleScaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (canWrite) {
                val fabExpanded = rememberFabExpanded(listState)
                AddFab(
                    label = R.string.tasks_add,
                    expanded = fabExpanded,
                    onClick = {
                        openEditor(null, -1)
                    },
                )
            }
        },
    ) { padding ->
        val snackbar = LocalSnackbarHostState.current
        val scope = rememberCoroutineScope()
        SaveFeedbackEffect(
            save = state.save,
            controls = viewModel.feedback,
            covered = editorVisible,
            errorText = { stringResource(it.stringRes()) },
            noticeText = { stringResource(it.stringRes()) },
        )
        CreatedUndoEffect(state.save.created, viewModel.feedback::consumeCreated) { viewModel.undoCreate(it.id) }
        val deletedMessage = stringResource(R.string.tasks_undo_deleted)
        val undoLabel = stringResource(R.string.undo)
        val completedMessage = stringResource(R.string.tasks_undo_completed)
        val deleteColor = MaterialTheme.colorScheme.error
        val checkColor = MaterialTheme.colorScheme.primary
        val deleteTaskWithUndo: (TaskItem) -> Unit = { task ->
            viewModel.deleteTask(spaceId, task.id) {
                scope.launchUndo(snackbar, deletedMessage, undoLabel) {
                    viewModel.restoreTask(spaceId, task)
                }
            }
        }
        // Completar y reabrir no esperan a la puerta de `isSaving`: cada tarea escribe a la suya, y un segundo toque sobre
        // la misma mientras se completa no hace nada.
        val setCompletedWithUndo: (TaskItem, Boolean) -> Unit = { task, completed ->
            if (!completing.isCompleting(task.id)) {
                val animate = completed && task.status != TaskStatus.DONE
                val write: (onFinished: (Boolean) -> Unit) -> Boolean = { onFinished ->
                    viewModel.setCompleted(spaceId, task, completed, onFinished = onFinished) {
                        scope.launchUndo(snackbar, completedMessage, undoLabel) {
                            viewModel.undoCompletion(spaceId, it)
                        }
                    }
                }
                // Solo se anima al completar una que no estaba hecha; si era la última de hoy, se celebra.
                if (animate) completing.begin(task.id, celebration, write) else write {}
            }
        }
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                val matching = remember(visibleTasks, query) { visibleTasks.filter { it.matchesQuery(query) } }
                val queried = completing.retain(matching, TaskItem::id)
                val sections = queried.groupIntoSections()
                if (canWrite) {
                    TopBarActions {
                        IconButton(onClick = { templatesVisible = true }) {
                            Icon(Icons.Rounded.Bolt, contentDescription = stringResource(R.string.tasks_templates_open))
                        }
                    }
                }
                // Filtros y búsqueda en la franja de la cabecera: se encoge con la barra al desplazar.
                CollapsibleHeaderBand {
                    Column(
                        Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        TaskFilterBar(state.filters, members, viewModel::setFilters)
                        SearchField(
                            query = query,
                            onQueryChange = { query = it },
                            visible = allTasks.isNotEmpty(),
                        )
                    }
                }
                when (val tasks = state.tasks) {
                    UiState.Loading -> LoadingState(Modifier.weight(1f))
                    is UiState.Error -> ErrorState(
                        Modifier.weight(1f),
                        message = stringResource(tasks.failure.messageRes()),
                        onRetry = { viewModel.observe(spaceId) },
                    )
                    is UiState.Content -> {
                        val taskRow: @Composable LazyItemScope.(TaskItem, RowPosition) -> Unit = { task, position ->
                            SwipeActionsBox(
                                startAction = SwipeAction(
                                    Icons.Rounded.Check,
                                    checkColor,
                                    stringResource(
                                        if (task.status ==
                                            TaskStatus.DONE
                                        ) {
                                            R.string.swipe_task_reopen
                                        } else {
                                            R.string.swipe_task_complete
                                        },
                                    ),
                                ) {
                                    setCompletedWithUndo(task, task.status != TaskStatus.DONE)
                                }.takeIf { canWrite },
                                endAction = SwipeAction(
                                    Icons.Rounded.Delete,
                                    deleteColor,
                                    stringResource(R.string.tasks_delete),
                                ) {
                                    deleteTaskWithUndo(task)
                                }.takeIf { enabled },
                                modifier = Modifier.padding(horizontal = Spacing.lg).animateItem()
                                    .completingExit(completing, task.id),
                            ) { accessibilityModifier ->
                                TaskListRow(
                                    task,
                                    today,
                                    position,
                                    enabled,
                                    canWrite,
                                    completing.isCompleting(task.id),
                                    onSetCompleted = { setCompletedWithUndo(task, it) },
                                    onEdit = {
                                        openEditor(task.id, -1)
                                    },
                                    onDelete = { deleteTaskWithUndo(task) },
                                    modifier = accessibilityModifier,
                                )
                            }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            // Deja hueco para que el FAB no tape el último elemento.
                            contentPadding = PaddingValues(bottom = Spacing.fabClearance),
                        ) {
                            item(key = "air") { Spacer(Modifier.height(Spacing.sm)) }
                            if (queried.isEmpty()) {
                                item(key = "empty") {
                                    TasksEmptyContent(
                                        empty = tasksEmptyState(allTasks, state.filters, query),
                                        onShowCompleted = {
                                            viewModel.setFilters(state.filters.copy(status = TaskStatus.DONE))
                                        },
                                        onClearFilters = {
                                            viewModel.clearFilters()
                                            query = ""
                                        },
                                        modifier = Modifier.fillParentMaxHeight(EMPTY_STATE_HEIGHT_FRACTION),
                                    )
                                }
                            }
                            sections.forEach { (section, sectionTasks) ->
                                val collapsible = section == TaskSection.COMPLETED
                                item(key = "header-$section") {
                                    SectionHeader(
                                        title = stringResource(section.titleRes),
                                        count = sectionTasks.size,
                                        expanded = completedExpanded.takeIf { collapsible },
                                        onToggle = if (collapsible) {
                                            { completedExpanded = !completedExpanded }
                                        } else {
                                            null
                                        },
                                        modifier = Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.sm),
                                    )
                                }
                                if (!collapsible || completedExpanded) {
                                    itemsIndexed(sectionTasks, key = { _, task -> task.id }) { index, task ->
                                        taskRow(task, rowPosition(index, sectionTasks.size))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Celebration(
                visible = celebration.visible,
                message = stringResource(R.string.celebration_today),
                onFinished = celebration::dismiss,
            )
        }
    }

    if (templatesVisible) {
        TaskTemplatePicker(
            onDismiss = { templatesVisible = false },
            onPick = { picked ->
                templatesVisible = false
                openEditor(null, homeTaskTemplates.indexOf(picked))
            },
        )
    }
    EditorSaveEffect(
        editor = state.save.editor,
        editorOpen = editorVisible,
        controls = viewModel.feedback,
        onClose = { editorVisible = false },
    )
    rememberEditorSlot(
        open = editorVisible,
        id = editorTaskId,
        items = (state.tasks as? UiState.Content)?.value,
        idOf = TaskItem::id,
        onGone = closeEditor,
    )?.let { slot ->
        val editorTask = slot.item
        TaskEditor(
            task = editorTask,
            template = editorTemplate,
            members = members,
            saving = state.save.isSaving,
            errorMessage = state.save.editorError?.let { stringResource(it.stringRes()) },
            onDismiss = closeEditor,
            onInvalidDate = viewModel::showInvalidDate,
            onSave = { title, description, assignee, due, priority, type, recurrence, start ->
                editorTask?.let {
                    viewModel.updateTask(
                        spaceId, it.id, title, description, assignee, due, priority, type, recurrence, start,
                    )
                } ?: run {
                    viewModel.createTask(
                        spaceId, title, description, assignee, due, priority, type, recurrence, start,
                    )
                }
            },
        )
    }
}

// Parte de la altura de la lista que ocupa el estado vacío, para que quede centrado bajo los filtros.
private const val EMPTY_STATE_HEIGHT_FRACTION = 0.75f

@Composable
private fun TasksEmptyContent(
    empty: TasksEmpty,
    onShowCompleted: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (empty.kind) {
        TasksEmptyKind.FIRST_RUN -> EmptyState(
            modifier = modifier,
            icon = Icons.Rounded.Checklist,
            title = stringResource(R.string.tasks_empty),
            description = stringResource(R.string.tasks_empty_description),
        )
        TasksEmptyKind.ALL_DONE -> EmptyState(
            modifier = modifier,
            icon = Icons.Rounded.Checklist,
            title = stringResource(R.string.tasks_all_done),
            actionLabel = stringResource(R.string.tasks_show_completed),
            onAction = onShowCompleted,
        )
        TasksEmptyKind.NO_RESULTS -> EmptyState(
            modifier = modifier,
            icon = Icons.Rounded.Checklist,
            title = stringResource(R.string.tasks_no_results),
            actionLabel = if (empty.canClearFilters) stringResource(R.string.tasks_clear_filters) else null,
            onAction = if (empty.canClearFilters) onClearFilters else null,
        )
    }
}

private val TaskSection.titleRes: Int
    get() = when (this) {
        TaskSection.OVERDUE -> R.string.tasks_overdue
        TaskSection.TODAY -> R.string.tasks_today
        TaskSection.UPCOMING -> R.string.tasks_upcoming
        TaskSection.NO_DATE -> R.string.tasks_no_date
        TaskSection.COMPLETED -> R.string.tasks_done
    }

// Barra de filtros: cuatro desplegables (estado, prioridad, tipo y responsable), dos por fila. Cada uno
// muestra su nombre y, dentro, el valor; con un valor distinto del de por defecto queda resaltado. La
// dimensión fecha ya la cubren las secciones de la lista (B3).
@Composable
internal fun TaskFilterBar(filters: TaskFilters, members: List<SpaceMember>, onChange: (TaskFilters) -> Unit) {
    val all = stringResource(R.string.tasks_filter_all)
    val any = stringResource(R.string.tasks_filter_any)
    val statusOptions = listOf(
        TaskStatus.TODO to stringResource(R.string.tasks_pending),
        TaskStatus.DONE to stringResource(R.string.tasks_done),
        null to all,
    )
    val priorityOptions = listOf<TaskPriority?>(null) + TaskPriority.entries.reversed()
    val unassigned = stringResource(R.string.tasks_filter_unassigned)
    val anyAssignee = filters.assigneeId != null || filters.unassignedOnly
    val assigneeValue = when {
        filters.unassignedOnly -> unassigned
        filters.assigneeId != null -> members.firstOrNull { it.userId == filters.assigneeId }?.displayName ?: any
        else -> any
    }
    FilterDropdownRow {
        FilterDropdown(
            label = stringResource(R.string.tasks_filter_status),
            value = statusOptions.first { it.first == filters.status }.second,
            options = statusOptions.map { (status, text) ->
                FilterOption(text, filters.status == status) { onChange(filters.copy(status = status)) }
            },
            active = filters.status != TaskFilters().status,
            modifier = Modifier.weight(1f),
        )
        FilterDropdown(
            label = stringResource(R.string.tasks_filter_priority),
            value = filters.priority?.label() ?: all,
            options = priorityOptions.map { priority ->
                FilterOption(
                    text = priority?.label() ?: all,
                    selected = filters.priority == priority,
                    icon = priority?.let { Icons.Rounded.Flag },
                    iconTint = priority?.accent(),
                ) {
                    onChange(filters.copy(priority = priority))
                }
            },
            active = filters.priority != null,
            modifier = Modifier.weight(1f),
        )
        FilterDropdown(
            label = stringResource(R.string.tasks_filter_type),
            value = filters.type?.let { stringResource(it.labelRes) } ?: any,
            options = listOf(FilterOption(any, filters.type == null) { onChange(filters.copy(type = null)) }) +
                TaskType.entries.map { taskType ->
                    FilterOption(
                        text = stringResource(taskType.labelRes),
                        selected = filters.type == taskType,
                        icon = taskType.icon,
                        iconTint = taskType.accent(),
                    ) {
                        onChange(filters.copy(type = taskType))
                    }
                },
            active = filters.type != null,
            modifier = Modifier.weight(1f),
        )
        FilterDropdown(
            label = stringResource(R.string.tasks_assignee),
            value = assigneeValue,
            options = listOf(
                FilterOption(any, !anyAssignee) { onChange(filters.copy(assigneeId = null, unassignedOnly = false)) },
                FilterOption(unassigned, filters.unassignedOnly) {
                    onChange(filters.copy(assigneeId = null, unassignedOnly = true))
                },
            ) + members.map { member ->
                FilterOption(member.displayName, filters.assigneeId == member.userId) {
                    onChange(filters.copy(assigneeId = member.userId, unassignedOnly = false))
                }
            },
            active = anyAssignee,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskListRow(
    task: TaskItem,
    today: LocalDate,
    position: RowPosition,
    enabled: Boolean,
    canToggle: Boolean,
    completing: Boolean,
    onSetCompleted: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = task.status == TaskStatus.DONE || completing
    val overdue = isOverdue(task, done, today, ZoneId.systemDefault())
    GroupedRow(
        position = position,
        modifier = modifier,
        tint = task.type?.let { categoryRowTint(it.accent()) },
        // No se usa `enabled` de la fila: la atenuaría (y la anunciaría «desactivada») sin conexión o al guardar.
        onClick = { if (enabled) onEdit() },
    ) {
        Checkbox(
            checked = done,
            onCheckedChange = onSetCompleted,
            // Completar no espera a otros guardados: solo depende de poder escribir.
            enabled = canToggle,
            modifier = Modifier.checkBounce(completing).semantics { contentDescription = task.title },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            task.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TaskMetaRow(task, overdue, done, today)
        }
        TaskCardMenu(taskTitle = task.title, enabled = enabled, onEdit = onEdit, onDelete = onDelete)
    }
}

@Composable
private fun TaskCardMenu(taskTitle: String, enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MoreOptionsButton(itemName = taskTitle, onClick = { expanded = true }, enabled = enabled)
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tasks_edit)) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tasks_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

// Selector de plantillas: al elegir una, abre el editor prerrellenado (título + tipo + recurrencia).
@Composable
private fun TaskTemplatePicker(onDismiss: () -> Unit, onPick: (TaskTemplate) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tasks_templates_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                homeTaskTemplates.forEach { template ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(template) }
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        template.type?.let {
                            Icon(
                                it.icon,
                                contentDescription = null,
                                tint = it.accent(),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(template.titleRes), style = MaterialTheme.typography.bodyLarge)
                            template.recurrence?.let { rec ->
                                Text(
                                    rec.label(),
                                    style = MaterialTheme.typography.bodyMedium,
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

@Composable
internal fun TaskEditor(
    task: TaskItem?,
    template: TaskTemplate?,
    members: List<SpaceMember>,
    saving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onInvalidDate: () -> Unit,
    onSave: (String, String?, String?, Instant?, TaskPriority, TaskType?, TaskRecurrence?, Instant?) -> Unit,
) {
    // Al crear desde una plantilla, se prerrellena título/tipo/recurrencia (editable antes de guardar).
    val templateTitle = template?.titleRes?.let { stringResource(it) }.orEmpty()
    val initial = TaskDraft.of(task, template, templateTitle)
    var draft by rememberSaveable(task?.id, template?.titleRes) {
        mutableStateOf(initial)
    }
    val validation = TaskValidation.validate(draft.title, draft.description)
    val form = rememberFormValidationState()
    val titleFocus = remember { FocusRequester() }
    val descriptionFocus = remember { FocusRequester() }
    val descriptionInvalid = validation == TaskUiMessage.DescriptionTooLong
    val titleError = form.visible(validation?.takeUnless { descriptionInvalid })
    val descriptionError = form.visible(validation?.takeIf { descriptionInvalid })
    BobitosFormSheet(
        title = stringResource(if (task == null) R.string.tasks_add_title else R.string.tasks_edit_title),
        confirmLabel = stringResource(R.string.save),
        saving = saving,
        dirty = { draft != initial },
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = {
            form.submit(
                isValid = validation == null,
                onValid = {
                    try {
                        onSave(
                            draft.title,
                            draft.description,
                            draft.assigneeId,
                            TaskValidation.parseDueDate(draft.dueDate),
                            draft.priority,
                            draft.type,
                            draft.recurrence,
                            TaskValidation.parseDueDate(draft.startDate),
                        )
                    } catch (_: InvalidTaskDateException) {
                        onInvalidDate()
                    }
                },
                onInvalid = { (if (descriptionInvalid) descriptionFocus else titleFocus).requestFocus() },
            )
        },
    ) {
        OutlinedTextField(
            draft.title,
            { draft = draft.copy(title = it) },
            label = { Text(stringResource(R.string.tasks_title_label)) },
            singleLine = true,
            isError = titleError != null,
            supportingText = errorSupportingText(titleError?.let { stringResource(it.stringRes()) }),
            modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
        )
        OutlinedTextField(
            draft.description,
            { draft = draft.copy(description = it) },
            label = { Text(stringResource(R.string.tasks_description_label)) },
            minLines = 2,
            isError = descriptionError != null,
            supportingText = errorSupportingText(descriptionError?.let { stringResource(it.stringRes()) }),
            modifier = Modifier.fillMaxWidth().focusRequester(descriptionFocus),
        )
        AssigneePicker(draft.assigneeId, members) { draft = draft.copy(assigneeId = it) }
        TaskDateFields(
            draft.startDate,
            { draft = draft.copy(startDate = it) },
            draft.dueDate,
            { draft = draft.copy(dueDate = it) },
        )
        PriorityPicker(draft.priority) { draft = draft.copy(priority = it) }
        TaskTypePicker(selected = draft.type, onSelect = { draft = draft.copy(type = it) })
        RecurrencePicker(
            selected = draft.recurrence,
            onSelect = {
                draft = draft.copy(recurrenceUnit = it?.unit, recurrenceInterval = it?.interval ?: 1)
            },
        )
    }
}

@Composable
private fun AssigneePicker(selectedId: String?, members: List<SpaceMember>, onSelect: (String?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { menu = true }) {
            Text(
                members.firstOrNull { it.userId == selectedId }?.displayName
                    ?: stringResource(R.string.tasks_unassigned),
            )
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tasks_unassigned)) },
                onClick = {
                    onSelect(null)
                    menu = false
                },
            )
            members.forEach { member ->
                DropdownMenuItem(
                    text = { Text(member.displayName) },
                    onClick = {
                        onSelect(member.userId)
                        menu = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PriorityPicker(selected: TaskPriority, onSelect: (TaskPriority) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        TaskPriority.entries.forEachIndexed { index, value ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, TaskPriority.entries.size),
                icon = {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(value.accent()),
                    )
                },
            ) {
                Text(value.label())
            }
        }
    }
}

// Línea de metadatos compacta (envuelve): estado/fecha, inicio, tipo, recurrencia, responsable y,
// si está hecha, quién la completó. Cada dato es un «chip» de icono + texto.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskMetaRow(task: TaskItem, overdue: Boolean, done: Boolean, today: LocalDate) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        TaskDateMeta(task, overdue, done, today)
        task.startAt?.let { start ->
            MetaChip(Icons.Rounded.CalendarMonth, stringResource(R.string.tasks_start_date_value, start.formatDate()))
        }
        MetaChip(
            icon = Icons.Rounded.Flag,
            text = task.priority.label(),
            iconTint = task.priority.accent(),
        )
        task.type?.let { type -> MetaChip(type.icon, stringResource(type.labelRes), type.accent()) }
        task.recurrence?.let { rec -> MetaChip(Icons.Rounded.Repeat, rec.label()) }
        MetaChip(
            icon = Icons.Rounded.Person,
            text = task.assigneeName ?: stringResource(R.string.tasks_unassigned),
            tint = if (task.assigneeId == null) MaterialTheme.colorScheme.error else null,
        )
        if (done) {
            MetaChip(
                icon = Icons.Rounded.Check,
                text = stringResource(R.string.tasks_completed_by, task.completedByName ?: task.completedBy.orEmpty()),
            )
        }
    }
}

// Chip de fecha con estado temporal: badge «Vencida»/«Hoy» resaltado, o la fecha de vencimiento.
// Sin fecha no se muestra chip (lo indica el badge de la sección en B3).
@Composable
private fun TaskDateMeta(task: TaskItem, overdue: Boolean, done: Boolean, today: LocalDate) {
    val dueDate = task.dueAt?.atZone(ZoneId.systemDefault())?.toLocalDate()
    when {
        overdue -> MetaChip(
            Icons.Rounded.CalendarMonth,
            stringResource(R.string.tasks_badge_overdue),
            MaterialTheme.colorScheme.error,
        )
        !done && dueDate == today -> MetaChip(
            Icons.Rounded.CalendarMonth,
            stringResource(R.string.tasks_today),
            MaterialTheme.colorScheme.primary,
        )
        task.dueAt != null -> MetaChip(Icons.Rounded.CalendarMonth, task.dueAt.formatDate())
    }
}

@Composable
internal fun MetaChip(icon: ImageVector, text: String, tint: Color? = null, iconTint: Color? = null) {
    val color = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = iconTint ?: color)
        Spacer(Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun TaskDateFields(
    startDate: String,
    onStartChange: (String) -> Unit,
    dueDate: String,
    onDueChange: (String) -> Unit,
) {
    DateField(stringResource(R.string.tasks_start_date_label), startDate, onStartChange)
    DateField(stringResource(R.string.tasks_due_date_label), dueDate, onDueChange)
}

@Composable
private fun DateField(label: String, value: String, onChange: (String) -> Unit) {
    val date = value.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    var showPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedButton(onClick = { showPicker = true }) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(date?.let(BobitosDates::shortDate) ?: stringResource(R.string.tasks_no_date))
            }
            if (date != null) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.tasks_clear_date))
                }
            }
        }
    }
    if (showPicker) {
        AppDatePickerDialog(
            initialDate = date ?: LocalDate.now(),
            onConfirm = {
                onChange(it.toString())
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun TaskTypePicker(selected: TaskType?, onSelect: (TaskType?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            if (selected != null) {
                Icon(selected.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                selected?.let { stringResource(it.labelRes) }
                    ?: stringResource(R.string.task_type_label),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.task_type_none)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            TaskType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    leadingIcon = { Icon(option.icon, contentDescription = null) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private enum class RecurrenceMode { NONE, DAILY, WEEKLY, MONTHLY, CUSTOM }

private val RecurrenceMode.labelRes: Int
    get() = when (this) {
        RecurrenceMode.NONE -> R.string.recurrence_none
        RecurrenceMode.DAILY -> R.string.recurrence_daily
        RecurrenceMode.WEEKLY -> R.string.recurrence_weekly
        RecurrenceMode.MONTHLY -> R.string.recurrence_monthly
        RecurrenceMode.CUSTOM -> R.string.recurrence_custom
    }

private val RecurrenceUnit.labelRes: Int
    get() = when (this) {
        RecurrenceUnit.DAY -> R.string.recurrence_unit_days
        RecurrenceUnit.WEEK -> R.string.recurrence_unit_weeks
        RecurrenceUnit.MONTH -> R.string.recurrence_unit_months
    }

private fun TaskRecurrence?.toMode(): RecurrenceMode = when {
    this == null -> RecurrenceMode.NONE
    interval != 1 -> RecurrenceMode.CUSTOM
    unit == RecurrenceUnit.DAY -> RecurrenceMode.DAILY
    unit == RecurrenceUnit.WEEK -> RecurrenceMode.WEEKLY
    else -> RecurrenceMode.MONTHLY
}

private fun RecurrenceMode.toRecurrence(current: TaskRecurrence?): TaskRecurrence? = when (this) {
    RecurrenceMode.NONE -> null
    RecurrenceMode.DAILY -> TaskRecurrence(RecurrenceUnit.DAY, 1)
    RecurrenceMode.WEEKLY -> TaskRecurrence(RecurrenceUnit.WEEK, 1)
    RecurrenceMode.MONTHLY -> TaskRecurrence(RecurrenceUnit.MONTH, 1)
    RecurrenceMode.CUSTOM -> current?.takeIf { it.interval != 1 } ?: TaskRecurrence(RecurrenceUnit.DAY, 2)
}

@Composable
private fun TaskRecurrence.label(): String = when {
    interval == 1 && unit == RecurrenceUnit.DAY -> stringResource(R.string.recurrence_daily)
    interval == 1 && unit == RecurrenceUnit.WEEK -> stringResource(R.string.recurrence_weekly)
    interval == 1 && unit == RecurrenceUnit.MONTH -> stringResource(R.string.recurrence_monthly)
    unit == RecurrenceUnit.DAY -> pluralStringResource(R.plurals.recurrence_every_n_days, interval, interval)
    unit == RecurrenceUnit.WEEK -> pluralStringResource(R.plurals.recurrence_every_n_weeks, interval, interval)
    else -> pluralStringResource(R.plurals.recurrence_every_n_months, interval, interval)
}

@Composable
private fun RecurrencePicker(selected: TaskRecurrence?, onSelect: (TaskRecurrence?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.recurrence_label), style = MaterialTheme.typography.labelLarge)
        Box {
            TextButton(onClick = { menu = true }) {
                Text(stringResource(selected.toMode().labelRes))
            }
            DropdownMenu(menu, { menu = false }) {
                RecurrenceMode.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes)) },
                        onClick = {
                            menu = false
                            onSelect(option.toRecurrence(selected))
                        },
                    )
                }
            }
        }
        if (selected != null && selected.interval != 1) {
            CustomRecurrenceFields(selected, onSelect)
        }
    }
}

@Composable
private fun CustomRecurrenceFields(recurrence: TaskRecurrence, onSelect: (TaskRecurrence?) -> Unit) {
    var unitMenu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(stringResource(R.string.recurrence_every))
        OutlinedTextField(
            value = recurrence.interval.toString(),
            onValueChange = { input ->
                val next = input.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, 365) ?: 1
                onSelect(recurrence.copy(interval = next))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(96.dp),
        )
        Box {
            TextButton(onClick = { unitMenu = true }) {
                Text(stringResource(recurrence.unit.labelRes))
            }
            DropdownMenu(unitMenu, { unitMenu = false }) {
                RecurrenceUnit.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes)) },
                        onClick = {
                            unitMenu = false
                            onSelect(recurrence.copy(unit = option))
                        },
                    )
                }
            }
        }
    }
}

@Composable internal fun TaskPriority.label() = stringResource(
    when (this) {
        TaskPriority.LOW -> R.string.tasks_priority_low
        TaskPriority.MEDIUM -> R.string.tasks_priority_medium
        TaskPriority.HIGH -> R.string.tasks_priority_high
    },
)

private fun Instant.formatDate() = BobitosDates.shortDate(atZone(ZoneId.systemDefault()).toLocalDate())
internal fun Instant.formatIsoDate() = atZone(ZoneId.systemDefault()).toLocalDate().toString()

internal fun TaskUiMessage.stringRes() = when (this) {
    TaskUiMessage.TitleRequired -> R.string.tasks_error_title_required
    TaskUiMessage.TitleTooLong -> R.string.tasks_error_title_too_long
    TaskUiMessage.DescriptionTooLong -> R.string.tasks_error_description_too_long
    TaskUiMessage.InvalidDate -> R.string.tasks_error_invalid_date
    TaskUiMessage.InvalidAssignee -> R.string.tasks_error_invalid_assignee
    TaskUiMessage.NotAuthenticated -> R.string.space_error_not_authenticated
    TaskUiMessage.EmailNotVerified -> R.string.space_error_email_not_verified
    TaskUiMessage.SpaceNotFound -> R.string.space_error_not_found
    TaskUiMessage.TaskNotFound -> R.string.tasks_error_not_found
    TaskUiMessage.TaskChanged -> R.string.tasks_error_changed
    TaskUiMessage.PermissionDenied -> R.string.space_error_permission_denied
    TaskUiMessage.NetworkError -> R.string.space_error_network
    TaskUiMessage.UnexpectedError -> R.string.space_error_unexpected
    TaskUiMessage.SaveTimeout -> R.string.write_timeout
    TaskUiMessage.TaskCreated -> R.string.tasks_notice_created
    TaskUiMessage.TaskUpdated -> R.string.tasks_notice_updated
    TaskUiMessage.TaskReopened -> R.string.tasks_notice_reopened
    TaskUiMessage.TaskDeleted -> R.string.tasks_notice_deleted
}
