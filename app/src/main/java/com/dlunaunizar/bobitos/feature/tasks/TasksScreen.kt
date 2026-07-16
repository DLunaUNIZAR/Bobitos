package com.dlunaunizar.bobitos.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TasksScreen(
    spaceId: String,
    state: TasksUiState,
    canWrite: Boolean,
    onObserve: (String) -> Unit,
    onStopObserving: () -> Unit,
    onFiltersChanged: (TaskFilters) -> Unit,
    onCreate: (String, String?, String?, Instant?, TaskPriority) -> Unit,
    onUpdate: (String, String, String?, String?, Instant?, TaskPriority) -> Unit,
    onSetCompleted: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onInvalidDate: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(spaceId) {
        onObserve(spaceId)
        onDispose(onStopObserving)
    }
    val members = (state.members as? UiState.Content)?.value.orEmpty()
    val allTasks = (state.tasks as? UiState.Content)?.value.orEmpty()
    val visibleTasks = state.filters.apply(allTasks)
    val enabled = canWrite && !state.isSaving
    var editorTask by remember { mutableStateOf<TaskItem?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    var deleteTask by remember { mutableStateOf<TaskItem?>(null) }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(stringResource(R.string.tasks_list_title), style = MaterialTheme.typography.headlineSmall)
                Text(pluralStringResource(
                    R.plurals.tasks_visible_count,
                    visibleTasks.size,
                    visibleTasks.size,
                    allTasks.size,
                ))
            }
            Button(enabled = enabled && members.isNotEmpty(), onClick = {
                editorTask = null; editorVisible = true
            }) { Text(stringResource(R.string.tasks_add)) }
        }
        TaskFeedback(state, onClearFeedback)
        TaskFilterBar(state.filters, members, onFiltersChanged)
        when (val tasks = state.tasks) {
            UiState.Loading -> Text(stringResource(R.string.generic_loading))
            is UiState.Error -> Text(
                tasks.message ?: stringResource(R.string.generic_error),
                color = MaterialTheme.colorScheme.error,
            )
            is UiState.Content -> if (visibleTasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (allTasks.isEmpty()) R.string.tasks_empty else R.string.tasks_no_results))
                }
            } else LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleTasks, key = TaskItem::id) { task ->
                    TaskCard(
                        task, enabled,
                        onSetCompleted = { onSetCompleted(task.id, it) },
                        onEdit = { editorTask = task; editorVisible = true },
                        onDelete = { deleteTask = task },
                    )
                }
            }
        }
    }

    if (editorVisible) TaskEditor(
        task = editorTask,
        members = members,
        saving = state.isSaving,
        onDismiss = { editorVisible = false },
        onInvalidDate = onInvalidDate,
        onSave = { title, description, assignee, due, priority ->
            editorTask?.let { onUpdate(it.id, title, description, assignee, due, priority) }
                ?: onCreate(title, description, assignee, due, priority)
            editorVisible = false
        },
    )
    deleteTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteTask = null },
            title = { Text(stringResource(R.string.tasks_delete_title)) },
            text = { Text(stringResource(R.string.tasks_delete_body, task.title)) },
            confirmButton = { TextButton(enabled = enabled, onClick = {
                onDelete(task.id); deleteTask = null
            }) { Text(stringResource(R.string.tasks_delete)) } },
            dismissButton = { TextButton(onClick = { deleteTask = null }) {
                Text(stringResource(R.string.cancel))
            } },
        )
    }
}

@Composable
private fun TaskFilterBar(
    filters: TaskFilters,
    members: List<SpaceMember>,
    onChange: (TaskFilters) -> Unit,
) {
    var assigneeMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextButton(onClick = {
            onChange(filters.copy(status = when (filters.status) {
                TaskStatus.TODO -> TaskStatus.DONE
                TaskStatus.DONE -> null
                null -> TaskStatus.TODO
            }))
        }) { Text(filters.status?.label() ?: stringResource(R.string.tasks_filter_all)) }
        TextButton(onClick = {
            onChange(filters.copy(priority = when (filters.priority) {
                null -> TaskPriority.HIGH
                TaskPriority.HIGH -> TaskPriority.MEDIUM
                TaskPriority.MEDIUM -> TaskPriority.LOW
                TaskPriority.LOW -> null
            }))
        }) { Text(filters.priority?.label() ?: stringResource(R.string.tasks_filter_priority)) }
        TextButton(onClick = {
            val values = TaskDateFilter.entries
            onChange(filters.copy(date = values[(filters.date.ordinal + 1) % values.size]))
        }) { Text(filters.date.label()) }
        Box {
            TextButton(onClick = { assigneeMenu = true }) {
                Text(
                    when {
                        filters.unassignedOnly -> stringResource(R.string.tasks_unassigned)
                        filters.assigneeId != null -> members.firstOrNull {
                            it.userId == filters.assigneeId
                        }?.displayName ?: stringResource(R.string.tasks_assignee)
                        else -> stringResource(R.string.tasks_assignee)
                    },
                )
            }
            DropdownMenu(expanded = assigneeMenu, onDismissRequest = { assigneeMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tasks_filter_all)) },
                    onClick = { onChange(filters.copy(assigneeId = null, unassignedOnly = false)); assigneeMenu = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tasks_unassigned)) },
                    onClick = { onChange(filters.copy(assigneeId = null, unassignedOnly = true)); assigneeMenu = false },
                )
                members.forEach { member -> DropdownMenuItem(
                    text = { Text(member.displayName) },
                    onClick = { onChange(filters.copy(assigneeId = member.userId, unassignedOnly = false)); assigneeMenu = false },
                ) }
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
    val overdue = task.status == TaskStatus.TODO && task.dueAt
        ?.atZone(ZoneId.systemDefault())
        ?.toLocalDate()
        ?.isBefore(LocalDate.now()) == true
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Checkbox(task.status == TaskStatus.DONE, onCheckedChange = onSetCompleted, enabled = enabled)
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.status == TaskStatus.DONE) TextDecoration.LineThrough else null,
                    color = if (overdue) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
                task.description?.let { Text(it) }
                Text(
                    task.assigneeName ?: stringResource(R.string.tasks_unassigned),
                    color = if (task.assigneeId == null) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
                Text("${task.priority.label()} · ${task.dueAt?.formatDate() ?: stringResource(R.string.tasks_no_date)}")
                Text(
                    stringResource(R.string.tasks_created_by, task.createdByName),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (task.status == TaskStatus.DONE) Text(
                    stringResource(R.string.tasks_completed_by, task.completedByName ?: task.completedBy.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row {
                    TextButton(enabled = enabled, onClick = onEdit) { Text(stringResource(R.string.tasks_edit)) }
                    TextButton(enabled = enabled, onClick = onDelete) { Text(stringResource(R.string.tasks_delete)) }
                }
            }
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
    onSave: (String, String?, String?, Instant?, TaskPriority) -> Unit,
) {
    var title by remember(task?.id) { mutableStateOf(task?.title.orEmpty()) }
    var description by remember(task?.id) { mutableStateOf(task?.description.orEmpty()) }
    var assigneeId by remember(task?.id) { mutableStateOf(task?.assigneeId ?: members.firstOrNull()?.userId) }
    var dueDate by remember(task?.id) { mutableStateOf(task?.dueAt?.formatIsoDate().orEmpty()) }
    var priority by remember(task?.id) { mutableStateOf(task?.priority ?: TaskPriority.MEDIUM) }
    var memberMenu by remember { mutableStateOf(false) }
    val validation = TaskValidation.validate(title, description, assigneeId)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (task == null) R.string.tasks_add_title else R.string.tasks_edit_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.tasks_title_label)) }, singleLine = true)
            OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.tasks_description_label)) }, minLines = 2)
            Box {
                TextButton(onClick = { memberMenu = true }) {
                    Text(members.firstOrNull { it.userId == assigneeId }?.displayName ?: stringResource(R.string.tasks_choose_assignee))
                }
                DropdownMenu(memberMenu, { memberMenu = false }) {
                    members.forEach { member -> DropdownMenuItem(
                        text = { Text(member.displayName) },
                        onClick = { assigneeId = member.userId; memberMenu = false },
                    ) }
                }
            }
            OutlinedTextField(dueDate, { dueDate = it }, label = { Text(stringResource(R.string.tasks_due_date_label)) }, singleLine = true)
            Row { TaskPriority.entries.forEach { value -> TextButton(onClick = { priority = value }) {
                Text(if (priority == value) "✓ ${value.label()}" else value.label())
            } } }
            validation?.let { Text(stringResource(it.stringRes()), color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = validation == null && !saving, onClick = {
            try {
                onSave(title, description, assigneeId, TaskValidation.parseDueDate(dueDate), priority)
            } catch (_: InvalidTaskDateException) { onInvalidDate() }
        }) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TaskFeedback(state: TasksUiState, onDismiss: () -> Unit) {
    val message = state.error ?: state.notice ?: return
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        color = if (state.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) { Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(message.stringRes()), Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
    } }
}

@Composable private fun TaskStatus.label() = stringResource(if (this == TaskStatus.TODO) R.string.tasks_pending else R.string.tasks_done)
@Composable private fun TaskPriority.label() = stringResource(when (this) {
    TaskPriority.LOW -> R.string.tasks_priority_low
    TaskPriority.MEDIUM -> R.string.tasks_priority_medium
    TaskPriority.HIGH -> R.string.tasks_priority_high
})
@Composable private fun TaskDateFilter.label() = stringResource(when (this) {
    TaskDateFilter.ALL -> R.string.tasks_filter_date
    TaskDateFilter.OVERDUE -> R.string.tasks_overdue
    TaskDateFilter.TODAY -> R.string.tasks_today
    TaskDateFilter.UPCOMING -> R.string.tasks_upcoming
    TaskDateFilter.NO_DATE -> R.string.tasks_no_date
})

private fun Instant.formatDate() = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault()).format(this)
private fun Instant.formatIsoDate() = atZone(ZoneId.systemDefault()).toLocalDate().toString()

private fun TaskUiMessage.stringRes() = when (this) {
    TaskUiMessage.TitleRequired -> R.string.tasks_error_title_required
    TaskUiMessage.TitleTooLong -> R.string.tasks_error_title_too_long
    TaskUiMessage.DescriptionTooLong -> R.string.tasks_error_description_too_long
    TaskUiMessage.AssigneeRequired -> R.string.tasks_error_assignee_required
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
