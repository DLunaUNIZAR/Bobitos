package com.dlunaunizar.bobitos.feature.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.AppDatePickerDialog
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosDialog
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskStatus
import com.dlunaunizar.bobitos.data.repository.EventInput
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters

@Composable
fun CalendarScreen(
    spaceId: String,
    canWrite: Boolean,
    modifier: Modifier = Modifier,
    initialEventId: String? = null,
    initialDate: LocalDate? = null,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // El editor sobrevive a una rotación: se guarda el id del evento y la hora de creación como texto.
    var editorEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var handledInitialEvent by rememberSaveable(initialEventId) { mutableStateOf(false) }
    var drilledFrom by remember { mutableStateOf<CalendarDisplayMode?>(null) }
    var creatingAtText by rememberSaveable { mutableStateOf<String?>(null) }
    var eventToDelete by remember { mutableStateOf<CalendarEvent?>(null) }
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.calendar_undo_deleted)
    val undoLabel = stringResource(R.string.undo)

    DisposableEffect(spaceId) {
        viewModel.observe(spaceId)
        onDispose(viewModel::stop)
    }

    LaunchedEffect(initialDate) {
        initialDate?.let(viewModel::goToDate)
    }

    val events = (state.events as? UiState.Content)?.value.orEmpty()
    val filteredEvents = events.forSelectedMembers(state.selectedMemberIds)
    val members = (state.members as? UiState.Content)?.value.orEmpty()
    val editor = editorEventId?.let { id -> events.firstOrNull { it.id == id } }
    val creatingAt = creatingAtText?.let(LocalTime::parse)

    LaunchedEffect(initialEventId, events) {
        if (initialEventId != null && !handledInitialEvent) {
            events.firstOrNull { it.id == initialEventId }?.let {
                editorEventId = it.id
                handledInitialEvent = true
            }
        }
    }

    BackHandler(enabled = drilledFrom != null) {
        drilledFrom?.let(viewModel::setMode)
        drilledFrom = null
    }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CalendarPeriodHeader(
                date = state.focusedDate,
                mode = state.mode,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
            )
            CalendarModeSelector(state.mode) { mode ->
                drilledFrom = null
                viewModel.setMode(mode)
            }
            MemberFilters(
                members = members,
                selectedIds = state.selectedMemberIds,
                onToggle = viewModel::toggleMember,
                onSelectAll = viewModel::selectAllMembers,
                onClear = viewModel::clearMemberSelection,
            )

            val onDayTap: (LocalDate) -> Unit = { date ->
                drilledFrom = state.mode
                viewModel.select(date)
                viewModel.setMode(CalendarDisplayMode.DAY)
            }
            when (state.mode) {
                CalendarDisplayMode.MONTH -> MonthGrid(
                    month = YearMonth.from(state.focusedDate),
                    selected = state.focusedDate,
                    events = filteredEvents,
                    select = onDayTap,
                )
                CalendarDisplayMode.DAY -> DayHourGrid(
                    events = filteredEvents.eventsOn(state.focusedDate),
                    tasks = state.tasks.tasksOn(state.focusedDate),
                    canWrite = canWrite,
                    onEdit = { editorEventId = it.id },
                    onDelete = { id -> eventToDelete = filteredEvents.firstOrNull { it.id == id } },
                    onCreateAt = { creatingAtText = it.toString() },
                    modifier = Modifier.weight(1f),
                )
                CalendarDisplayMode.WEEK -> WeekEventList(
                    focusedDate = state.focusedDate,
                    events = filteredEvents,
                    canWrite = canWrite,
                    onEdit = { editorEventId = it.id },
                    onDelete = { id -> eventToDelete = filteredEvents.firstOrNull { it.id == id } },
                    onDayTap = onDayTap,
                    modifier = Modifier.weight(1f),
                )
            }

            state.message?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
                LaunchedEffect(message) { viewModel.clearMessage() }
            }
        }

        if (canWrite) {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.calendar_new_event)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }

    CalendarEditorHost(
        editorEventId = editorEventId,
        editor = editor,
        creating = creating,
        creatingAt = creatingAt,
        eventsLoaded = state.events is UiState.Content,
        day = state.focusedDate,
        members = members,
        saving = state.saving,
        canWrite = canWrite,
        onDropUnresolved = { editorEventId = null },
        onClose = {
            creating = false
            editorEventId = null
            creatingAtText = null
        },
        onSave = { id, input -> viewModel.save(id, input) },
    )

    eventToDelete?.let { event ->
        DeleteEventDialog(
            event = event,
            enabled = canWrite && !state.saving,
            onConfirm = {
                viewModel.delete(event.id)
                eventToDelete = null
                scope.launchUndo(snackbar, deletedMessage, undoLabel) {
                    viewModel.save(null, event.toInput())
                }
            },
            onDismiss = { eventToDelete = null },
        )
    }
}

@Composable
internal fun CalendarPeriodHeader(
    date: LocalDate,
    mode: CalendarDisplayMode,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val label = when (mode) {
        CalendarDisplayMode.DAY -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
        CalendarDisplayMode.WEEK -> {
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sunday = monday.plusDays(6)
            "${monday.format(DateTimeFormatter.ofPattern("d MMM"))} – " +
                sunday.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        }
        CalendarDisplayMode.MONTH -> YearMonth.from(date)
            .format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(R.string.calendar_previous_period),
            )
        }
        Text(label, style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.calendar_next_period),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarModeSelector(selected: CalendarDisplayMode, onSelected: (CalendarDisplayMode) -> Unit) {
    val modes = CalendarDisplayMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                label = {
                    Text(
                        stringResource(
                            when (mode) {
                                CalendarDisplayMode.DAY -> R.string.calendar_mode_day
                                CalendarDisplayMode.WEEK -> R.string.calendar_mode_week
                                CalendarDisplayMode.MONTH -> R.string.calendar_mode_month
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun MemberFilters(
    members: List<SpaceMember>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    Text(stringResource(R.string.calendar_people), style = MaterialTheme.typography.titleSmall)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { AssistChip(onClick = onSelectAll, label = { Text(stringResource(R.string.calendar_all)) }) }
        item { AssistChip(onClick = onClear, label = { Text(stringResource(R.string.calendar_none)) }) }
        items(members, key = SpaceMember::userId) { member ->
            FilterChip(
                selected = member.userId in selectedIds,
                onClick = { onToggle(member.userId) },
                label = { Text(member.displayName) },
            )
        }
    }
}

@Composable
internal fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    events: List<CalendarEvent>,
    select: (LocalDate) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val interval = month.visibleInterval(zone)
    val first = interval.start.atZone(zone).toLocalDate()
    val today = LocalDate.now()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach { initial ->
                Text(
                    text = initial,
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics {},
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        (0L until 42L).map(first::plusDays).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        dayEvents = events.eventsOn(date),
                        isSelected = date == selected,
                        isToday = date == today,
                        inMonth = YearMonth.from(date) == month,
                        onClick = { select(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    dayEvents: List<CalendarEvent>,
    isSelected: Boolean,
    isToday: Boolean,
    inMonth: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = pluralStringResource(
        R.plurals.calendar_day_events,
        dayEvents.size,
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
        dayEvents.size,
    )
    val numberColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .then(
                        when {
                            isSelected -> Modifier.background(MaterialTheme.colorScheme.primary)
                            isToday -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else -> Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    color = numberColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                dayEvents.take(3).forEach { event ->
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(event.color.accent()),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekEventList(
    focusedDate: LocalDate,
    events: List<CalendarEvent>,
    canWrite: Boolean,
    onEdit: (CalendarEvent) -> Unit,
    onDelete: (String) -> Unit,
    onDayTap: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monday = focusedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    LazyColumn(
        modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        (0L..6L).forEach { offset ->
            val date = monday.plusDays(offset)
            item("header-$date") {
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEEE d")),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDayTap(date) }
                        .padding(vertical = 4.dp),
                )
            }
            val dayEvents = events.eventsOn(date)
            if (dayEvents.isEmpty()) {
                item("empty-$date") {
                    Text(
                        stringResource(R.string.calendar_no_events),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(dayEvents, key = { "$date-${it.id}" }) { event ->
                    EventRow(event, canWrite, { onEdit(event) }, { onDelete(event.id) })
                }
            }
        }
    }
}

@Composable
private fun TaskOnDayRow(task: TaskItem) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Checklist,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.status == TaskStatus.DONE) {
                        TextDecoration.LineThrough
                    } else {
                        null
                    },
                )
                task.assigneeName?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DayHourGrid(
    events: List<CalendarEvent>,
    tasks: List<TaskItem>,
    canWrite: Boolean,
    onEdit: (CalendarEvent) -> Unit,
    onDelete: (String) -> Unit,
    onCreateAt: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val allDay = events.filter(CalendarEvent::allDay)
    val timedByHour = events.filterNot(CalendarEvent::allDay)
        .groupBy { it.startAt.atZone(zone).toLocalTime().hour }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 88.dp)) {
        if (allDay.isNotEmpty() || tasks.isNotEmpty()) {
            item(key = "allday-header") {
                Text(
                    text = stringResource(R.string.calendar_all_day),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(tasks, key = { "task-${it.id}" }) { task -> TaskOnDayRow(task) }
            items(allDay, key = { "allday-${it.id}" }) { event ->
                EventRow(event, canWrite, { onEdit(event) }, { onDelete(event.id) })
            }
            item(key = "hours-divider") { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        }
        items((0..23).toList(), key = { "hour-$it" }) { hour ->
            HourRow(
                hour = hour,
                events = timedByHour[hour].orEmpty(),
                canWrite = canWrite,
                onEdit = onEdit,
                onDelete = onDelete,
                onLongPress = { onCreateAt(LocalTime.of(hour, 0)) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HourRow(
    hour: Int,
    events: List<CalendarEvent>,
    canWrite: Boolean,
    onEdit: (CalendarEvent) -> Unit,
    onDelete: (String) -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = canWrite,
                onClick = {},
                onLongClickLabel = stringResource(R.string.calendar_create_at_hour),
                onLongClick = onLongPress,
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = LocalTime.of(hour, 0).format(hourFormatter),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(52.dp)
                .padding(top = 4.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            events.forEach { event ->
                EventRow(event, canWrite, { onEdit(event) }, { onDelete(event.id) })
            }
        }
    }
}

private val hourFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun EventRow(event: CalendarEvent, canWrite: Boolean, edit: () -> Unit, delete: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = canWrite, onClick = edit),
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .padding(end = 12.dp)
                    .size(width = 4.dp, height = 40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(event.color.accent()),
            )
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (event.allDay) {
                        stringResource(R.string.calendar_all_day)
                    } else {
                        event.startAt.atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (event.participantNames.isNotEmpty()) {
                        event.participantNames.joinToString()
                    } else {
                        stringResource(R.string.calendar_general_event)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canWrite) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.more_options),
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.calendar_edit)) },
                            onClick = {
                                menuExpanded = false
                                edit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.calendar_delete)) },
                            onClick = {
                                menuExpanded = false
                                delete()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun List<TaskItem>.tasksOn(date: LocalDate): List<TaskItem> {
    val zone = ZoneId.systemDefault()
    return filter { task ->
        val start = task.startAt?.atZone(zone)?.toLocalDate()
        val due = task.dueAt?.atZone(zone)?.toLocalDate()
        val from = start ?: due
        val to = due ?: start
        from != null &&
            to != null &&
            !date.isBefore(minOf(from, to)) &&
            !date.isAfter(maxOf(from, to))
    }.sortedBy { it.startAt ?: it.dueAt }
}

internal fun List<CalendarEvent>.eventsOn(date: LocalDate): List<CalendarEvent> {
    val zone = ZoneId.systemDefault()
    val interval = date.visibleInterval(CalendarDisplayMode.DAY, zone)
    return filter { it.overlaps(interval.start, interval.endExclusive) }.sortedBy { it.startAt }
}

@Composable
internal fun EventEditor(
    event: CalendarEvent?,
    day: LocalDate,
    initialStart: LocalTime?,
    members: List<SpaceMember>,
    saving: Boolean,
    canWrite: Boolean,
    dismiss: () -> Unit,
    save: (String?, EventInput) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initial = EventDraft.of(event, day, initialStart, zone)
    var draft by rememberSaveable(event?.id, initialStart?.toString(), stateSaver = EventDraftSaver) {
        mutableStateOf(initial)
    }
    var activePicker by remember { mutableStateOf<EventPicker?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val dateError = stringResource(R.string.calendar_date_error)
    val startLabelRes = if (draft.allDay) R.string.calendar_start_date_label else R.string.calendar_start_datetime_label
    val endLabelRes = if (draft.allDay) R.string.calendar_end_date_label else R.string.calendar_end_datetime_label

    BobitosFormSheet(
        title = stringResource(if (event == null) R.string.calendar_new_event else R.string.calendar_edit_event_title),
        confirmLabel = stringResource(R.string.calendar_save),
        confirmEnabled = canWrite,
        saving = saving,
        dirty = draft != initial,
        onDismiss = dismiss,
        onConfirm = {
            val input = buildEventInput(
                title = draft.title,
                description = draft.description,
                allDay = draft.allDay,
                startDate = draft.startDate,
                endDate = draft.endDate,
                startTime = draft.startTime,
                endTime = draft.endTime,
                zone = zone,
                color = draft.color,
                participants = draft.selectedIds,
            )
            if (input == null) {
                error = dateError
            } else {
                save(event?.id, input)
            }
        },
    ) {
        OutlinedTextField(
            draft.title,
            { draft = draft.copy(title = it) },
            label = { Text(stringResource(R.string.calendar_event_title_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            draft.description,
            { draft = draft.copy(description = it) },
            label = { Text(stringResource(R.string.calendar_event_description_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(draft.allDay, { draft = draft.copy(allDay = it) })
            Text(stringResource(R.string.calendar_all_day))
        }
        DateTimeField(
            label = stringResource(startLabelRes),
            date = draft.startDate,
            time = if (draft.allDay) null else draft.startTime,
            onDateClick = { activePicker = EventPicker.START_DATE },
            onTimeClick = { activePicker = EventPicker.START_TIME },
        )
        DateTimeField(
            label = stringResource(endLabelRes),
            date = draft.endDate,
            time = if (draft.allDay) null else draft.endTime,
            onDateClick = { activePicker = EventPicker.END_DATE },
            onTimeClick = { activePicker = EventPicker.END_TIME },
        )
        Text(stringResource(R.string.calendar_color_label), style = MaterialTheme.typography.labelLarge)
        ColorPicker(selected = draft.color, onSelect = { draft = draft.withColor(it) })
        if (members.isNotEmpty()) {
            Text(stringResource(R.string.calendar_participants_label))
        }
        members.forEach { member ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    member.userId in draft.selectedIds,
                    { checked -> draft = draft.withParticipant(member.userId, checked) },
                )
                Text(member.displayName)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    EventPickers(
        draft = draft,
        activePicker = activePicker,
        onDraft = { draft = it },
        onClose = { activePicker = null },
    )
}

@Composable
private fun EventPickers(
    draft: EventDraft,
    activePicker: EventPicker?,
    onDraft: (EventDraft) -> Unit,
    onClose: () -> Unit,
) {
    when (activePicker) {
        EventPicker.START_DATE -> AppDatePickerDialog(
            initialDate = draft.startDate,
            onConfirm = {
                onDraft(draft.withStartDate(it))
                onClose()
            },
            onDismiss = onClose,
        )
        EventPicker.END_DATE -> AppDatePickerDialog(
            initialDate = draft.endDate,
            onConfirm = {
                onDraft(draft.withEndDate(it))
                onClose()
            },
            onDismiss = onClose,
        )
        EventPicker.START_TIME -> EventTimePickerDialog(
            initialTime = draft.startTime,
            onConfirm = {
                onDraft(draft.withStartTime(it))
                onClose()
            },
            onDismiss = onClose,
        )
        EventPicker.END_TIME -> EventTimePickerDialog(
            initialTime = draft.endTime,
            onConfirm = {
                onDraft(draft.withEndTime(it))
                onClose()
            },
            onDismiss = onClose,
        )
        null -> Unit
    }
}

// Muestra el editor de evento (nuevo, desde una hora o existente). Si el evento que se editaba ya no
// existe, lo descarta (mientras la lista carga, espera).
@Composable
internal fun CalendarEditorHost(
    editorEventId: String?,
    editor: CalendarEvent?,
    creating: Boolean,
    creatingAt: LocalTime?,
    eventsLoaded: Boolean,
    day: LocalDate,
    members: List<SpaceMember>,
    saving: Boolean,
    canWrite: Boolean,
    onDropUnresolved: () -> Unit,
    onClose: () -> Unit,
    onSave: (String?, EventInput) -> Unit,
) {
    val unresolved = editorEventId != null && editor == null
    LaunchedEffect(unresolved, eventsLoaded) {
        if (unresolved && eventsLoaded) onDropUnresolved()
    }
    if (creating || creatingAt != null || editor != null) {
        EventEditor(
            event = editor,
            day = day,
            initialStart = creatingAt,
            members = members,
            saving = saving,
            canWrite = canWrite,
            dismiss = onClose,
        ) { id, input ->
            onSave(id, input)
            onClose()
        }
    }
}

@Composable
internal fun DeleteEventDialog(event: CalendarEvent, enabled: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    BobitosDialog(
        title = stringResource(R.string.calendar_delete_title),
        message = stringResource(R.string.calendar_delete_body, event.title),
        confirmLabel = stringResource(R.string.calendar_delete),
        destructive = true,
        confirmEnabled = enabled,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

// Cada opción es un botón de radio de 48 dp (círculo de 32 dp dentro): el seleccionado lleva un ✓ además
// del borde, así no depende solo del color, y las opciones saltan de línea con fuentes grandes.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(selected: EventColor, onSelect: (EventColor) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        EventColor.entries.forEach { option ->
            val label = stringResource(option.labelRes)
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(option.accent())
                        .then(
                            if (isSelected) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

private enum class EventPicker { START_DATE, END_DATE, START_TIME, END_TIME }

private val editorDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val editorTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun DateTimeField(
    label: String,
    date: LocalDate,
    time: LocalTime?,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDateClick) {
                Icon(
                    Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(date.format(editorDateFormatter))
            }
            if (time != null) {
                OutlinedButton(onClick = onTimeClick) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(time.format(editorTimeFormatter))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventTimePickerDialog(initialTime: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.accept))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimePicker(state = state) },
    )
}

// Reconstruye el input desde un evento existente, para re-crearlo al «Deshacer» un borrado.
private fun CalendarEvent.toInput(): EventInput = EventInput(
    title = title,
    description = description,
    allDay = allDay,
    startAt = startAt,
    endAt = endAt,
    startDate = startDate,
    endDateExclusive = endDateExclusive,
    timeZone = timeZone,
    color = color,
    participantIds = participantIds,
)

private fun buildEventInput(
    title: String,
    description: String,
    allDay: Boolean,
    startDate: LocalDate,
    endDate: LocalDate,
    startTime: LocalTime,
    endTime: LocalTime,
    zone: ZoneId,
    color: EventColor,
    participants: List<String>,
): EventInput? {
    val startInstant: Instant
    val endInstant: Instant
    val startDateOut: LocalDate?
    val endDateOut: LocalDate?
    if (allDay) {
        startDateOut = startDate
        endDateOut = endDate.plusDays(1)
        startInstant = startDate.atStartOfDay(zone).toInstant()
        endInstant = endDateOut.atStartOfDay(zone).toInstant()
    } else {
        startDateOut = null
        endDateOut = null
        startInstant = startDate.atTime(startTime).atZone(zone).toInstant()
        endInstant = endDate.atTime(endTime).atZone(zone).toInstant()
    }
    val valid = if (allDay) !endDate.isBefore(startDate) else endInstant.isAfter(startInstant)
    if (!valid) return null
    return EventInput(
        title,
        description,
        allDay,
        startInstant,
        endInstant,
        startDateOut,
        endDateOut,
        zone.id,
        color,
        participants,
    )
}
