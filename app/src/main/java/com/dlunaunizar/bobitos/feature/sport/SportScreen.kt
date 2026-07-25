package com.dlunaunizar.bobitos.feature.sport

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.designsystem.theme.categoryCardColors
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SportActivity
import com.dlunaunizar.bobitos.core.model.SportType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SportScreen(
    spaceId: String,
    canWrite: Boolean,
    modifier: Modifier = Modifier,
    viewModel: SportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(spaceId) {
        viewModel.observe(spaceId)
        onDispose { viewModel.stopObserving() }
    }
    val members = (state.members as? UiState.Content)?.value.orEmpty()
    val actionsEnabled = canWrite && !state.isSaving
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.sport_undo_deleted)
    val undoLabel = stringResource(R.string.undo)
    var editor by remember { mutableStateOf<ActivityEditorRequest?>(null) }
    var activityToDelete by remember { mutableStateOf<SportActivity?>(null) }

    val deleteWithUndo: (SportActivity) -> Unit = { activity ->
        viewModel.deleteActivity(activity.id)
        scope.launchUndo(snackbar, deletedMessage, undoLabel) {
            viewModel.addActivity(activity.date, activity.type, activity.name, activity.participantIds)
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            WeekSelector(
                weekDays = state.weekDays,
                focusedDate = state.focusedDate,
                onPrevious = viewModel::previousWeek,
                onNext = viewModel::nextWeek,
                onSelectDay = viewModel::selectDay,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = state.focusedDate.formatHeader(),
                style = MaterialTheme.typography.titleMedium,
            )
            SportFeedback(state, viewModel::clearFeedback)
            when (val activities = state.activities) {
                UiState.Loading -> LoadingState(Modifier.weight(1f))
                is UiState.Error -> ErrorState(Modifier.weight(1f), message = activities.message)
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
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            contentPadding = PaddingValues(bottom = 88.dp),
                        ) {
                            items(dayActivities, key = SportActivity::id) { activity ->
                                ActivityCard(
                                    activity = activity,
                                    enabled = actionsEnabled,
                                    canWrite = canWrite,
                                    onEdit = { editor = ActivityEditorRequest(activity) },
                                    onDelete = { activityToDelete = activity },
                                    onToggleDone = { viewModel.setDone(activity.id, !activity.done) },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (canWrite) {
            ExtendedFloatingActionButton(
                onClick = { editor = ActivityEditorRequest(null) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.sport_add)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.lg),
            )
        }
    }

    editor?.let { request ->
        ActivityEditor(
            request = request,
            members = members,
            saving = state.isSaving,
            canWrite = canWrite,
            onDismiss = { editor = null },
            onSave = { type, name, participantIds ->
                request.activity?.let {
                    viewModel.updateActivity(it.id, it.date, type, name, participantIds)
                } ?: viewModel.addActivity(state.focusedDate, type, name, participantIds)
                editor = null
            },
        )
    }
    activityToDelete?.let { activity ->
        AlertDialog(
            onDismissRequest = { activityToDelete = null },
            title = { Text(stringResource(R.string.sport_delete_title)) },
            text = { Text(stringResource(R.string.sport_delete_body, activity.name)) },
            confirmButton = {
                TextButton(enabled = actionsEnabled, onClick = {
                    deleteWithUndo(activity)
                    activityToDelete = null
                }) { Text(stringResource(R.string.sport_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { activityToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun WeekSelector(
    weekDays: List<LocalDate>,
    focusedDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.sport_prev_week))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            weekDays.forEach { day ->
                DayChip(
                    date = day,
                    selected = day == focusedDate,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectDay(day) },
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.sport_next_week))
        }
    }
}

@Composable
private fun DayChip(date: LocalDate, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Column(
            Modifier.padding(vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(date.dayInitial(), style = MaterialTheme.typography.labelSmall)
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun SportFeedback(state: SportUiState, onDismiss: () -> Unit) {
    val message = state.error ?: state.notice ?: return
    Surface(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        color = if (state.error != null) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Row(Modifier.padding(start = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(message.stringResourceId), Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        }
    }
}

@Composable
private fun ActivityCard(
    activity: SportActivity,
    enabled: Boolean,
    canWrite: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleDone: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = activity.participantNames.takeIf(List<String>::isNotEmpty)?.joinToString(", ")
                        ?: stringResource(R.string.sport_no_participants),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (activity.done) {
                    Text(
                        text = stringResource(R.string.sport_done_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
            }
            if (canWrite) {
                Box {
                    IconButton(enabled = enabled, onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
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

@Composable
private fun ActivityEditor(
    request: ActivityEditorRequest,
    members: List<SpaceMember>,
    saving: Boolean,
    canWrite: Boolean,
    onDismiss: () -> Unit,
    onSave: (SportType, String, List<String>) -> Unit,
) {
    val activity = request.activity
    var type by remember(activity?.id) { mutableStateOf(activity?.type ?: SportType.PADEL) }
    var name by remember(activity?.id) { mutableStateOf(activity?.name.orEmpty()) }
    var selected by remember(activity?.id) { mutableStateOf(activity?.participantIds?.toSet().orEmpty()) }
    val typeLabel = stringResource(type.labelRes)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (activity == null) R.string.sport_add_title else R.string.sport_edit_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SportType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            leadingIcon = { Icon(option.icon, contentDescription = null, tint = option.accent()) },
                            label = { Text(stringResource(option.labelRes)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.sport_name_label)) },
                    placeholder = { Text(typeLabel) },
                    singleLine = true,
                )
                if (members.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.sport_participants_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    members.forEach { member ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = member.userId in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + member.userId else selected - member.userId
                                },
                            )
                            Text(member.displayName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canWrite && !saving,
                onClick = { onSave(type, name.ifBlank { typeLabel }, selected.toList()) },
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private data class ActivityEditorRequest(val activity: SportActivity?)

private fun LocalDate.formatHeader(): String {
    val formatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES"))
    return format(formatter).replaceFirstChar { it.uppercase() }
}

private fun LocalDate.dayInitial(): String =
    dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("es", "ES"))
        .take(1)
        .uppercase()

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
        SportUiMessage.ActivityAdded -> R.string.sport_notice_added
        SportUiMessage.ActivityUpdated -> R.string.sport_notice_updated
        SportUiMessage.ActivityDeleted -> R.string.sport_notice_deleted
        SportUiMessage.ActivityDone -> R.string.sport_notice_done
        SportUiMessage.ActivityPending -> R.string.sport_notice_pending
    }
