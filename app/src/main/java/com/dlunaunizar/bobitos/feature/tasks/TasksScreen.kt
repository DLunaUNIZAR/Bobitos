package com.dlunaunizar.bobitos.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.AppDatePickerDialog
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.SearchField
import com.dlunaunizar.bobitos.core.designsystem.component.SwipeAction
import com.dlunaunizar.bobitos.core.designsystem.component.SwipeActionsBox
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.designsystem.theme.categoryCardColors
import com.dlunaunizar.bobitos.core.model.RecurrenceUnit
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskRecurrence
import com.dlunaunizar.bobitos.core.model.TaskStatus
import com.dlunaunizar.bobitos.core.model.TaskType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val visibleTasks = state.filters.apply(allTasks)
    val enabled = canWrite && !state.isSaving
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.tasks_undo_deleted)
    val undoLabel = stringResource(R.string.undo)
    val deleteColor = MaterialTheme.colorScheme.error
    val checkColor = MaterialTheme.colorScheme.primary
    val deleteTaskWithUndo: (TaskItem) -> Unit = { task ->
        viewModel.deleteTask(spaceId, task.id)
        scope.launchUndo(snackbar, deletedMessage, undoLabel) {
            viewModel.createTask(
                spaceId,
                task.title,
                task.description,
                task.assigneeId,
                task.dueAt,
                task.priority,
                task.type,
                task.recurrence,
                task.startAt,
            )
        }
    }
    var editorTask by remember { mutableStateOf<TaskItem?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    var deleteTask by remember { mutableStateOf<TaskItem?>(null) }
    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(stringResource(R.string.tasks_list_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    pluralStringResource(
                        R.plurals.tasks_visible_count,
                        visibleTasks.size,
                        visibleTasks.size,
                        allTasks.size,
                    ),
                )
            }
            Button(enabled = enabled && members.isNotEmpty(), onClick = {
                editorTask = null
                editorVisible = true
            }) { Text(stringResource(R.string.tasks_add)) }
        }
        TaskFeedback(state, viewModel::clearFeedback)
        TaskFilterBar(state.filters, members, viewModel::setFilters)
        SearchField(
            query = query,
            onQueryChange = { query = it },
            visible = allTasks.isNotEmpty(),
            modifier = Modifier.padding(vertical = 4.dp),
        )
        val queried = visibleTasks.filter { it.matchesQuery(query) }
        val sections = queried.groupIntoSections()
        when (val tasks = state.tasks) {
            UiState.Loading -> LoadingState(Modifier.weight(1f))
            is UiState.Error -> ErrorState(Modifier.weight(1f), message = tasks.message)
            is UiState.Content -> if (queried.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Checklist,
                    title = stringResource(if (allTasks.isEmpty()) R.string.tasks_empty else R.string.tasks_no_results),
                )
            } else {
                val taskRow: @Composable LazyItemScope.(TaskItem) -> Unit = { task ->
                    SwipeActionsBox(
                        startAction = SwipeAction(Icons.Rounded.Check, checkColor) {
                            viewModel.setCompleted(spaceId, task.id, task.status != TaskStatus.DONE)
                        }.takeIf { enabled },
                        endAction = SwipeAction(Icons.Rounded.Delete, deleteColor) {
                            deleteTaskWithUndo(task)
                        }.takeIf { enabled },
                        modifier = Modifier.animateItem(),
                    ) {
                        TaskCard(
                            task,
                            enabled,
                            onSetCompleted = { viewModel.setCompleted(spaceId, task.id, it) },
                            onEdit = {
                                editorTask = task
                                editorVisible = true
                            },
                            onDelete = { deleteTask = task },
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sections.forEach { (section, sectionTasks) ->
                        val collapsible = section == TaskSection.COMPLETED
                        item(key = "header-$section") {
                            TaskSectionHeader(
                                title = stringResource(section.titleRes),
                                count = sectionTasks.size,
                                collapsible = collapsible,
                                expanded = completedExpanded,
                                onToggle = { completedExpanded = !completedExpanded },
                            )
                        }
                        if (!collapsible || completedExpanded) {
                            items(sectionTasks, key = TaskItem::id) { task -> taskRow(task) }
                        }
                    }
                }
            }
        }
    }

    if (editorVisible) {
        TaskEditor(
            task = editorTask,
            members = members,
            saving = state.isSaving,
            onDismiss = { editorVisible = false },
            onInvalidDate = viewModel::showInvalidDate,
            onSave = { title, description, assignee, due, priority, type, recurrence, start ->
                editorTask?.let {
                    viewModel.updateTask(
                        spaceId, it.id, title, description, assignee, due, priority, type, recurrence, start,
                    )
                } ?: viewModel.createTask(
                    spaceId,
                    title,
                    description,
                    assignee,
                    due,
                    priority,
                    type,
                    recurrence,
                    start,
                )
                editorVisible = false
            },
        )
    }
    deleteTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteTask = null },
            title = { Text(stringResource(R.string.tasks_delete_title)) },
            text = { Text(stringResource(R.string.tasks_delete_body, task.title)) },
            confirmButton = {
                TextButton(enabled = enabled, onClick = {
                    deleteTaskWithUndo(task)
                    deleteTask = null
                }) { Text(stringResource(R.string.tasks_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTask = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
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

// Cabecera «Título · N». La de Completadas es plegable (colapsada por defecto).
@Composable
private fun TaskSectionHeader(
    title: String,
    count: Int,
    collapsible: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val label = "$title · $count"
    if (!collapsible) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.xs))
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = stringResource(R.string.tasks_completed_toggle),
        )
    }
}

// Barra de filtros con FilterChip de estado visible. Estado y prioridad son chips directos
// (single-select con toggle: tocar el activo lo desmarca); tipo y responsable abren un menú.
// La dimensión fecha ya la cubren las secciones de la lista (B3).
@Composable
private fun TaskFilterBar(filters: TaskFilters, members: List<SpaceMember>, onChange: (TaskFilters) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = filters.status == TaskStatus.TODO,
            onClick = {
                onChange(filters.copy(status = if (filters.status == TaskStatus.TODO) null else TaskStatus.TODO))
            },
            label = { Text(stringResource(R.string.tasks_pending)) },
        )
        FilterChip(
            selected = filters.status == TaskStatus.DONE,
            onClick = {
                onChange(filters.copy(status = if (filters.status == TaskStatus.DONE) null else TaskStatus.DONE))
            },
            label = { Text(stringResource(R.string.tasks_done)) },
        )
        TaskPriority.entries.forEach { option ->
            FilterChip(
                selected = filters.priority == option,
                onClick = {
                    onChange(filters.copy(priority = if (filters.priority == option) null else option))
                },
                label = { Text(option.label()) },
            )
        }
        TaskTypeFilterChip(filters, onChange)
        TaskAssigneeFilterChip(filters, members, onChange)
    }
}

@Composable
private fun TaskTypeFilterChip(filters: TaskFilters, onChange: (TaskFilters) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = filters.type != null,
            onClick = { menu = true },
            label = {
                Text(filters.type?.let { stringResource(it.labelRes) } ?: stringResource(R.string.tasks_filter_type))
            },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tasks_filter_all)) },
                onClick = {
                    onChange(filters.copy(type = null))
                    menu = false
                },
            )
            TaskType.entries.forEach { taskType ->
                DropdownMenuItem(
                    text = { Text(stringResource(taskType.labelRes)) },
                    leadingIcon = { Icon(taskType.icon, contentDescription = null, tint = taskType.accent()) },
                    onClick = {
                        onChange(filters.copy(type = taskType))
                        menu = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TaskAssigneeFilterChip(filters: TaskFilters, members: List<SpaceMember>, onChange: (TaskFilters) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val label = when {
        filters.unassignedOnly -> stringResource(R.string.tasks_unassigned)
        filters.assigneeId != null ->
            members.firstOrNull { it.userId == filters.assigneeId }?.displayName
                ?: stringResource(R.string.tasks_assignee)
        else -> stringResource(R.string.tasks_assignee)
    }
    Box {
        FilterChip(
            selected = filters.assigneeId != null || filters.unassignedOnly,
            onClick = { menu = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tasks_filter_all)) },
                onClick = {
                    onChange(filters.copy(assigneeId = null, unassignedOnly = false))
                    menu = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tasks_unassigned)) },
                onClick = {
                    onChange(filters.copy(assigneeId = null, unassignedOnly = true))
                    menu = false
                },
            )
            members.forEach { member ->
                DropdownMenuItem(
                    text = { Text(member.displayName) },
                    onClick = {
                        onChange(filters.copy(assigneeId = member.userId, unassignedOnly = false))
                        menu = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskItem,
    enabled: Boolean,
    onSetCompleted: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val done = task.status == TaskStatus.DONE
    val overdue = !done &&
        task.dueAt
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDate()
            ?.isBefore(LocalDate.now()) == true
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = task.type?.let { categoryCardColors(it.accent()) } ?: CardDefaults.cardColors(),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = done,
                onCheckedChange = onSetCompleted,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = task.title },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PriorityDot(task.priority)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                task.description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TaskMetaRow(task, overdue, done)
            }
            TaskCardMenu(enabled = enabled, onEdit = onEdit, onDelete = onDelete)
        }
    }
}

@Composable
private fun PriorityDot(priority: TaskPriority) {
    val description = stringResource(R.string.tasks_priority_cd, priority.label())
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(priority.accent())
            .semantics { contentDescription = description },
    )
}

@Composable
private fun TaskCardMenu(enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
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

@Composable
private fun TaskEditor(
    task: TaskItem?,
    members: List<SpaceMember>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onInvalidDate: () -> Unit,
    onSave: (String, String?, String?, Instant?, TaskPriority, TaskType?, TaskRecurrence?, Instant?) -> Unit,
) {
    var title by remember(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var description by remember(task?.id) { mutableStateOf(task?.description.orEmpty()) }
    // Por defecto «sin responsable» en tareas nuevas (responsable opcional); al editar se conserva el actual.
    var assigneeId by remember(task?.id) { mutableStateOf(task?.assigneeId) }
    var startDate by remember(task?.id) { mutableStateOf(task?.startAt?.formatIsoDate().orEmpty()) }
    var dueDate by remember(task?.id) { mutableStateOf(task?.dueAt?.formatIsoDate().orEmpty()) }
    var priority by remember(task?.id) { mutableStateOf(task?.priority ?: TaskPriority.MEDIUM) }
    var type by remember(task?.id) { mutableStateOf(task?.type) }
    var recurrence by remember(task?.id) { mutableStateOf(task?.recurrence) }
    var memberMenu by remember { mutableStateOf(false) }
    val validation = TaskValidation.validate(title, description)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (task == null) R.string.tasks_add_title else R.string.tasks_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(title, {
                    title = it
                }, label = { Text(stringResource(R.string.tasks_title_label)) }, singleLine = true)
                OutlinedTextField(description, {
                    description = it
                }, label = { Text(stringResource(R.string.tasks_description_label)) }, minLines = 2)
                Box {
                    TextButton(onClick = { memberMenu = true }) {
                        Text(
                            members.firstOrNull { it.userId == assigneeId }?.displayName
                                ?: stringResource(R.string.tasks_unassigned),
                        )
                    }
                    DropdownMenu(memberMenu, { memberMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tasks_unassigned)) },
                            onClick = {
                                assigneeId = null
                                memberMenu = false
                            },
                        )
                        members.forEach { member ->
                            DropdownMenuItem(
                                text = { Text(member.displayName) },
                                onClick = {
                                    assigneeId = member.userId
                                    memberMenu = false
                                },
                            )
                        }
                    }
                }
                TaskDateFields(startDate, { startDate = it }, dueDate, { dueDate = it })
                Row {
                    TaskPriority.entries.forEach { value ->
                        TextButton(onClick = { priority = value }) {
                            Text(if (priority == value) "✓ ${value.label()}" else value.label())
                        }
                    }
                }
                TaskTypePicker(selected = type, onSelect = { type = it })
                RecurrencePicker(selected = recurrence, onSelect = { recurrence = it })
                validation?.let { Text(stringResource(it.stringRes()), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = validation == null && !saving, onClick = {
                try {
                    onSave(
                        title,
                        description,
                        assigneeId,
                        TaskValidation.parseDueDate(dueDate),
                        priority,
                        type,
                        recurrence,
                        TaskValidation.parseDueDate(startDate),
                    )
                } catch (_: InvalidTaskDateException) {
                    onInvalidDate()
                }
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// Línea de metadatos compacta (envuelve): estado/fecha, inicio, tipo, recurrencia, responsable y,
// si está hecha, quién la completó. Cada dato es un «chip» de icono + texto.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskMetaRow(task: TaskItem, overdue: Boolean, done: Boolean) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        TaskDateMeta(task, overdue, done)
        task.startAt?.let { start ->
            MetaChip(Icons.Rounded.CalendarMonth, stringResource(R.string.tasks_start_date_value, start.formatDate()))
        }
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
private fun TaskDateMeta(task: TaskItem, overdue: Boolean, done: Boolean) {
    val dueDate = task.dueAt?.atZone(ZoneId.systemDefault())?.toLocalDate()
    when {
        overdue -> MetaChip(
            Icons.Rounded.CalendarMonth,
            stringResource(R.string.tasks_badge_overdue),
            MaterialTheme.colorScheme.error,
        )
        !done && dueDate == LocalDate.now() -> MetaChip(
            Icons.Rounded.CalendarMonth,
            stringResource(R.string.tasks_today),
            MaterialTheme.colorScheme.primary,
        )
        task.dueAt != null -> MetaChip(Icons.Rounded.CalendarMonth, task.dueAt.formatDate())
    }
}

@Composable
private fun MetaChip(icon: ImageVector, text: String, tint: Color? = null) {
    val color = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { showPicker = true }) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(date?.format(taskDateDisplayFormatter) ?: stringResource(R.string.tasks_no_date))
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

private val taskDateDisplayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

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
    unit == RecurrenceUnit.DAY -> stringResource(R.string.recurrence_every_n_days, interval)
    unit == RecurrenceUnit.WEEK -> stringResource(R.string.recurrence_every_n_weeks, interval)
    else -> stringResource(R.string.recurrence_every_n_months, interval)
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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

@Composable
private fun TaskFeedback(state: TasksUiState, onDismiss: () -> Unit) {
    val message = state.error ?: state.notice ?: return
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        color = if (state.error !=
            null
        ) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(message.stringRes()), Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        }
    }
}

@Composable private fun TaskPriority.label() = stringResource(
    when (this) {
        TaskPriority.LOW -> R.string.tasks_priority_low
        TaskPriority.MEDIUM -> R.string.tasks_priority_medium
        TaskPriority.HIGH -> R.string.tasks_priority_high
    },
)

private fun Instant.formatDate() =
    DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault()).format(this)
private fun Instant.formatIsoDate() = atZone(ZoneId.systemDefault()).toLocalDate().toString()

// Coincidencia por texto (título o descripción) para el buscador; en blanco no filtra.
private fun TaskItem.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val trimmed = query.trim()
    return title.contains(trimmed, ignoreCase = true) || description?.contains(trimmed, ignoreCase = true) == true
}

private fun TaskUiMessage.stringRes() = when (this) {
    TaskUiMessage.TitleRequired -> R.string.tasks_error_title_required
    TaskUiMessage.TitleTooLong -> R.string.tasks_error_title_too_long
    TaskUiMessage.DescriptionTooLong -> R.string.tasks_error_description_too_long
    TaskUiMessage.InvalidDate -> R.string.tasks_error_invalid_date
    TaskUiMessage.InvalidAssignee -> R.string.tasks_error_invalid_assignee
    TaskUiMessage.NotAuthenticated -> R.string.space_error_not_authenticated
    TaskUiMessage.EmailNotVerified -> R.string.space_error_email_not_verified
    TaskUiMessage.SpaceNotFound -> R.string.space_error_not_found
    TaskUiMessage.TaskNotFound -> R.string.tasks_error_not_found
    TaskUiMessage.PermissionDenied -> R.string.space_error_permission_denied
    TaskUiMessage.NetworkError -> R.string.space_error_network
    TaskUiMessage.UnexpectedError -> R.string.space_error_unexpected
    TaskUiMessage.TaskCreated -> R.string.tasks_notice_created
    TaskUiMessage.TaskUpdated -> R.string.tasks_notice_updated
    TaskUiMessage.TaskCompleted -> R.string.tasks_notice_completed
    TaskUiMessage.TaskReopened -> R.string.tasks_notice_reopened
    TaskUiMessage.TaskDeleted -> R.string.tasks_notice_deleted
}
