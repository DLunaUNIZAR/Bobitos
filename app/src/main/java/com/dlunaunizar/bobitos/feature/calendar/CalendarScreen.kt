package com.dlunaunizar.bobitos.feature.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.BobitosDates
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.messageRes
import com.dlunaunizar.bobitos.core.designsystem.component.AddFab
import com.dlunaunizar.bobitos.core.designsystem.component.AppDatePickerDialog
import com.dlunaunizar.bobitos.core.designsystem.component.AppLocale
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.designsystem.component.ClickableCard
import com.dlunaunizar.bobitos.core.designsystem.component.CreatedUndoEffect
import com.dlunaunizar.bobitos.core.designsystem.component.EditorSaveEffect
import com.dlunaunizar.bobitos.core.designsystem.component.FeedbackSnackbarEffect
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.ModuleHeaderBand
import com.dlunaunizar.bobitos.core.designsystem.component.ModuleScaffold
import com.dlunaunizar.bobitos.core.designsystem.component.MoreOptionsButton
import com.dlunaunizar.bobitos.core.designsystem.component.discardFailure
import com.dlunaunizar.bobitos.core.designsystem.component.errorSupportingText
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.designsystem.component.rememberEditorSlot
import com.dlunaunizar.bobitos.core.designsystem.component.rememberFormValidationState
import com.dlunaunizar.bobitos.core.designsystem.rememberFabExpanded
import com.dlunaunizar.bobitos.core.designsystem.rememberToday
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskStatus
import com.dlunaunizar.bobitos.data.repository.EventInput
import com.dlunaunizar.bobitos.feature.common.CheckboxRow
import com.dlunaunizar.bobitos.feature.common.MemberCheckboxes
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
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

    val loadedEvents = (state.events as? UiState.Content)?.value
    val filteredEvents = loadedEvents.orEmpty().forSelectedMembers(state.selectedMemberIds)
    val members = (state.members as? UiState.Content)?.value.orEmpty()
    val creatingAt = creatingAtText?.let(LocalTime::parse)

    LaunchedEffect(initialEventId, loadedEvents) {
        if (initialEventId != null && !handledInitialEvent) {
            loadedEvents?.firstOrNull { it.id == initialEventId }?.let {
                editorEventId = it.id
                handledInitialEvent = true
            }
        }
    }

    BackHandler(enabled = drilledFrom != null) {
        drilledFrom?.let(viewModel::setMode)
        drilledFrom = null
    }

    val editorOpen = creating || editorEventId != null || creatingAtText != null
    // El FAB sigue la lista de la vista abierta; el mes no se desplaza.
    val dayListState = rememberLazyListState()
    val weekListState = rememberLazyListState()
    val monthListState = rememberLazyListState()
    val fabListState = when (state.mode) {
        CalendarDisplayMode.DAY -> dayListState
        CalendarDisplayMode.WEEK -> weekListState
        CalendarDisplayMode.MONTH -> monthListState
    }
    ModuleScaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (canWrite) {
                val fabExpanded = rememberFabExpanded(fabListState)
                AddFab(
                    label = R.string.calendar_new_event,
                    expanded = fabExpanded,
                    onClick = { creating = true },
                )
            }
        },
    ) { padding ->
        val snackbar = LocalSnackbarHostState.current
        val deleteWithUndo: (CalendarEvent) -> Unit = { event ->
            viewModel.delete(event.id) {
                scope.launchUndo(snackbar, deletedMessage, undoLabel) {
                    viewModel.restore(event.id, event.toInput())
                }
            }
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Periodo, vista y personas fijos en la franja: el calendario es navegación, no se desplaza.
            ModuleHeaderBand {
                Column(
                    Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
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
                    PeopleFilter(
                        members = members,
                        selectedIds = state.selectedMemberIds,
                        onToggle = viewModel::toggleMember,
                        onSelectAll = viewModel::selectAllMembers,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val onDayTap: (LocalDate) -> Unit = { date ->
                    drilledFrom = state.mode
                    viewModel.select(date)
                    viewModel.setMode(CalendarDisplayMode.DAY)
                }
                EventsLoadState(state.events, state.refreshing, viewModel::retryEvents, Modifier.weight(1f)) {
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
                            onDelete = { id -> filteredEvents.firstOrNull { it.id == id }?.let(deleteWithUndo) },
                            onCreateAt = { creatingAtText = it.toString() },
                            modifier = Modifier.weight(1f),
                            deleteEnabled = !state.saving,
                            listState = dayListState,
                        )
                        CalendarDisplayMode.WEEK -> WeekEventList(
                            focusedDate = state.focusedDate,
                            events = filteredEvents,
                            canWrite = canWrite,
                            onEdit = { editorEventId = it.id },
                            onDelete = { id -> filteredEvents.firstOrNull { it.id == id }?.let(deleteWithUndo) },
                            onDayTap = onDayTap,
                            modifier = Modifier.weight(1f),
                            deleteEnabled = !state.saving,
                            listState = weekListState,
                        )
                    }
                }

                FeedbackSnackbarEffect(
                    error = state.error?.messageRes(),
                    notice = state.notice?.messageRes(),
                    editorSave = state.editorSave,
                    editorOpen = editorOpen,
                    onShown = viewModel::clearMessage,
                    text = { stringResource(it) },
                )
                CreatedUndoEffect(state.created, viewModel::consumeCreated) { viewModel.undoCreate(it.id) }
            }
        }
    }

    val closeEditor = {
        // Cerrar tras un guardado fallido descarta ese error (ya se veía en la hoja).
        state.editorSave.discardFailure(viewModel::clearMessage)
        creating = false
        editorEventId = null
        creatingAtText = null
    }
    EditorSaveEffect(
        status = state.editorSave,
        editorOpen = editorOpen,
        onClose = closeEditor,
        onConsume = viewModel::consumeEditorSave,
    )
    CalendarEditorHost(
        editorEventId = editorEventId,
        events = (state.events as? UiState.Content)?.value,
        creating = creating,
        creatingAt = creatingAt,
        day = state.focusedDate,
        members = members,
        saving = state.saving,
        errorMessage = state.error
            ?.takeIf { state.editorSave == EditorSaveStatus.FAILED }
            ?.let { stringResource(it.messageRes()) },
        canWrite = canWrite,
        onClose = closeEditor,
        onSave = { id, input -> viewModel.save(id, input) },
    )
}

private fun CalendarNotice.messageRes(): Int = when (this) {
    CalendarNotice.SAVED -> R.string.calendar_notice_saved
}

@Composable
internal fun CalendarPeriodHeader(
    date: LocalDate,
    mode: CalendarDisplayMode,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val label = when (mode) {
        CalendarDisplayMode.DAY -> BobitosDates.longDay(date)
        CalendarDisplayMode.WEEK -> {
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sunday = monday.plusDays(6)
            BobitosDates.weekRange(monday, sunday)
        }
        CalendarDisplayMode.MONTH -> BobitosDates.month(YearMonth.from(date))
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
internal fun PeopleFilter(
    members: List<SpaceMember>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MultiFilter(
        label = stringResource(R.string.calendar_people),
        allText = stringResource(R.string.calendar_all),
        countPlural = R.plurals.calendar_people_count,
        items = members.map { it.userId to it.displayName },
        selectedIds = selectedIds,
        onToggle = onToggle,
        onSelectAll = onSelectAll,
        tag = "filtro_personas",
        modifier = modifier,
    )
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
    val today = rememberToday()
    // Las celdas son cuadradas: si el mes no cabe en el alto que queda (barra abierta, letra grande), se desplaza, y
    // al hacerlo encoge la barra del marco, en vez de aplastar la última semana.
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup()) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.map(BobitosDates::dayInitial).forEach { initial ->
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
internal fun DayCell(
    date: LocalDate,
    dayEvents: List<CalendarEvent>,
    isSelected: Boolean,
    isToday: Boolean,
    inMonth: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eventsDescription = pluralStringResource(
        R.plurals.calendar_day_events,
        dayEvents.size,
        BobitosDates.accessibleDate(date),
        dayEvents.size,
    )
    val description = if (isToday) "$eventsDescription, ${stringResource(R.string.today)}" else eventsDescription
    val numberColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
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
internal fun WeekEventList(
    focusedDate: LocalDate,
    events: List<CalendarEvent>,
    canWrite: Boolean,
    onEdit: (CalendarEvent) -> Unit,
    onDelete: (String) -> Unit,
    onDayTap: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    deleteEnabled: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
) {
    val monday = focusedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val rows = rememberWeekRows(monday, events, rememberToday()) { events.eventsOn(it).isNotEmpty() }
    var expandedRuns by rememberExpandedRuns(monday)
    LazyColumn(
        modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = Spacing.fabClearance),
    ) {
        fun LazyListScope.dayItems(date: LocalDate) {
            item("header-$date") {
                Text(
                    BobitosDates.dayHeader(date),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() }
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
                    EventRow(event, canWrite, { onEdit(event) }, { onDelete(event.id) }, deleteEnabled)
                }
            }
        }
        weekRowItems(
            rows,
            expandedRuns,
            onToggle = { start ->
                expandedRuns =
                    if (start in expandedRuns) expandedRuns - start else expandedRuns + start
            },
            dayItems = { date -> dayItems(date) },
        )
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
    deleteEnabled: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
) {
    val zone = ZoneId.systemDefault()
    val allDay = events.filter(CalendarEvent::allDay)
    val timedByHour = events.filterNot(CalendarEvent::allDay)
        .groupBy { it.startAt.atZone(zone).toLocalTime().hour }
    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(bottom = Spacing.fabClearance)) {
        if (canWrite) {
            item(key = "hold-hint") {
                Text(
                    text = stringResource(R.string.calendar_hold_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
        }
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
                EventRow(event, canWrite, { onEdit(event) }, { onDelete(event.id) }, deleteEnabled)
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
                deleteEnabled = deleteEnabled,
            )
        }
    }
}

@Composable
internal fun HourRow(
    hour: Int,
    events: List<CalendarEvent>,
    canWrite: Boolean,
    onEdit: (CalendarEvent) -> Unit,
    onDelete: (String) -> Unit,
    onLongPress: () -> Unit,
    deleteEnabled: Boolean = true,
) {
    val createLabel = stringResource(R.string.calendar_create_at, BobitosDates.time(LocalTime.of(hour, 0)))
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val haptics = LocalHapticFeedback.current
    val pressSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (canWrite) {
                    // Al pulsar la fila se ilumina y, al completar el mantenido, vibra: así se nota que va a crear.
                    Modifier
                        .indication(pressSource, LocalIndication.current)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val press = PressInteraction.Press(offset)
                                    pressSource.emit(press)
                                    val released = tryAwaitRelease()
                                    pressSource.emit(
                                        if (released) {
                                            PressInteraction.Release(
                                                press,
                                            )
                                        } else {
                                            PressInteraction.Cancel(press)
                                        },
                                    )
                                },
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentOnLongPress()
                                },
                            )
                        }
                        .semantics(mergeDescendants = true) {
                            customActions = listOf(
                                CustomAccessibilityAction(createLabel) {
                                    onLongPress()
                                    true
                                },
                            )
                        }
                } else {
                    Modifier
                },
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = BobitosDates.time(LocalTime.of(hour, 0)),
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
                EventRow(event, canWrite, { onEdit(event) }, { onDelete(event.id) }, deleteEnabled)
            }
        }
    }
}

@Composable
internal fun EventRow(
    event: CalendarEvent,
    canWrite: Boolean,
    edit: () -> Unit,
    delete: () -> Unit,
    // Falso mientras otra escritura está en curso: borrar sería un toque que no hace nada.
    deleteEnabled: Boolean = true,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val content: @Composable ColumnScope.() -> Unit = {
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
                        BobitosDates.dateTime(event.startAt.atZone(ZoneId.systemDefault()).toLocalDateTime())
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
                    MoreOptionsButton(itemName = event.title, onClick = { menuExpanded = true })
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
                            enabled = deleteEnabled,
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
    ClickableCard(edit.takeIf { canWrite }, Modifier.fillMaxWidth(), content = content)
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
    errorMessage: String?,
    canWrite: Boolean,
    dismiss: () -> Unit,
    save: (String?, EventInput) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initial = EventDraft.of(event, day, initialStart, zone)
    var draft by rememberSaveable(event?.id, initialStart?.toString()) {
        mutableStateOf(initial)
    }
    var activePicker by remember { mutableStateOf<EventPicker?>(null) }
    val form = rememberFormValidationState()
    val titleFocus = remember { FocusRequester() }
    val descriptionFocus = remember { FocusRequester() }
    val endFocus = remember { FocusRequester() }
    val validation = CalendarValidation.validate(draft.title, draft.description)
    val input = draft.toEventInput(zone)
    val descriptionInvalid = validation == CalendarValidation.Error.DescriptionTooLong
    val titleError = form.visible(validation?.takeUnless { descriptionInvalid })
    val descriptionError = form.visible(validation?.takeIf { descriptionInvalid })
    val datesError = form.visible(stringResource(R.string.calendar_date_error).takeIf { input == null })

    BobitosFormSheet(
        title = stringResource(if (event == null) R.string.calendar_new_event else R.string.calendar_edit_event_title),
        confirmLabel = stringResource(R.string.calendar_save),
        canSubmit = canWrite,
        saving = saving,
        dirty = { draft != initial },
        errorMessage = errorMessage,
        onDismiss = dismiss,
        onConfirm = {
            form.submit(
                isValid = validation == null && input != null,
                onValid = { input?.let { save(event?.id, it) } },
                onInvalid = {
                    firstInvalidFocus(validation, titleFocus, descriptionFocus, endFocus).requestFocus()
                },
            )
        },
    ) {
        OutlinedTextField(
            draft.title,
            { draft = draft.copy(title = it) },
            label = { Text(stringResource(R.string.calendar_event_title_label)) },
            singleLine = true,
            isError = titleError != null,
            supportingText = errorSupportingText(titleError?.let { stringResource(it.stringResourceId) }),
            modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
        )
        OutlinedTextField(
            draft.description,
            { draft = draft.copy(description = it) },
            label = { Text(stringResource(R.string.calendar_event_description_label)) },
            isError = descriptionError != null,
            supportingText = errorSupportingText(descriptionError?.let { stringResource(it.stringResourceId) }),
            modifier = Modifier.fillMaxWidth().focusRequester(descriptionFocus),
        )
        CheckboxRow(
            label = stringResource(R.string.calendar_all_day),
            checked = draft.allDay,
            onCheckedChange = { draft = draft.copy(allDay = it) },
        )
        EventDateFields(
            draft = draft,
            error = datesError,
            endFocus = endFocus,
            onPicker = { activePicker = it },
        )
        Text(stringResource(R.string.calendar_color_label), style = MaterialTheme.typography.labelLarge)
        ColorPicker(selected = draft.color, onSelect = { draft = draft.copy(color = it) })
        MemberCheckboxes(
            label = stringResource(R.string.calendar_participants_label),
            members = members,
            selectedIds = draft.selectedIds,
            onToggle = { userId, selected -> draft = draft.withParticipant(userId, selected) },
        )
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
                onDraft(draft.copy(startDate = it))
                onClose()
            },
            onDismiss = onClose,
        )
        EventPicker.END_DATE -> AppDatePickerDialog(
            initialDate = draft.endDate,
            onConfirm = {
                onDraft(draft.copy(endDate = it))
                onClose()
            },
            onDismiss = onClose,
        )
        EventPicker.START_TIME -> EventTimePickerDialog(
            initialTime = draft.startTime,
            onConfirm = {
                onDraft(draft.copy(startTime = it))
                onClose()
            },
            onDismiss = onClose,
        )
        EventPicker.END_TIME -> EventTimePickerDialog(
            initialTime = draft.endTime,
            onConfirm = {
                onDraft(draft.copy(endTime = it))
                onClose()
            },
            onDismiss = onClose,
        )
        null -> Unit
    }
}

private fun firstInvalidFocus(
    validation: CalendarValidation.Error?,
    title: FocusRequester,
    description: FocusRequester,
    dates: FocusRequester,
) = when (validation) {
    null -> dates
    CalendarValidation.Error.DescriptionTooLong -> description
    else -> title
}

// Muestra el editor de evento (nuevo, desde una hora o existente). Si el evento que se editaba ya no
// existe, lo descarta (mientras la lista carga, espera).
@Composable
internal fun CalendarEditorHost(
    editorEventId: String?,
    events: List<CalendarEvent>?,
    creating: Boolean,
    creatingAt: LocalTime?,
    day: LocalDate,
    members: List<SpaceMember>,
    saving: Boolean,
    canWrite: Boolean,
    errorMessage: String?,
    onClose: () -> Unit,
    onSave: (String?, EventInput) -> Unit,
) {
    val slot = rememberEditorSlot(
        open = editorEventId != null,
        id = editorEventId,
        items = events,
        idOf = CalendarEvent::id,
        onGone = onClose,
    )
    if (creating || creatingAt != null || slot != null) {
        EventEditor(
            event = slot?.item,
            day = day,
            initialStart = creatingAt,
            members = members,
            saving = saving,
            errorMessage = errorMessage,
            canWrite = canWrite,
            dismiss = onClose,
        ) { id, input ->
            onSave(id, input)
        }
    }
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
            val swatch = option.accent()
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
                        .background(swatch)
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
                            tint = swatchCheckColor(swatch),
                            modifier = Modifier.size(18.dp),
                        )
                    }
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
    AppLocale {
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
}

private fun EventDraft.toEventInput(zone: ZoneId): EventInput? = buildEventInput(
    title = title,
    description = description,
    allDay = allDay,
    startDate = startDate,
    endDate = endDate,
    startTime = startTime,
    endTime = endTime,
    zone = zone,
    color = color,
    participants = selectedIds,
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
