package com.dlunaunizar.bobitos.feature.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.SyncStatusBanner
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.core.model.SyncStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun PersonalCalendarScreen(
    userId: String,
    spaces: List<SpaceSummary>,
    syncStatus: SyncStatus,
    canWrite: Boolean,
    onEventSelected: (spaceId: String, eventId: String, date: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: PersonalCalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(userId, spaces) { viewModel.observe(userId, spaces) }
    DisposableEffect(Unit) { onDispose(viewModel::stop) }
    var drilledFrom by remember { mutableStateOf<CalendarDisplayMode?>(null) }
    // El editor sobrevive a una rotación: se guardan espacio, id del evento y hora de creación.
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorSpaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorStartText by rememberSaveable { mutableStateOf<String?>(null) }
    var spacePickerOpen by remember { mutableStateOf(false) }
    var spacePickerTime by remember { mutableStateOf<LocalTime?>(null) }
    var eventToDelete by remember { mutableStateOf<PersonalCalendarEvent?>(null) }

    val events = (state.events as? UiState.Content)?.value.orEmpty()
        .filter { it.spaceId in state.selectedSpaceIds }
    val dayEvents = events.eventsOn(state.focusedDate)
    val dayEventsById = dayEvents.associateBy { it.event.id }

    val openEditor: (String, CalendarEvent?, LocalTime?) -> Unit = { spaceId, event, initialStart ->
        editorSpaceId = spaceId
        editorEventId = event?.id
        editorStartText = initialStart?.toString()
        editorOpen = true
        viewModel.observeEditorMembers(spaceId)
    }
    // Un solo espacio: se abre el editor directamente; con varios, primero se elige espacio.
    val onCreateAt: (LocalTime?) -> Unit = { time ->
        val single = spaces.singleOrNull()
        if (single != null) {
            openEditor(single.id, null, time)
        } else {
            spacePickerTime = time
            spacePickerOpen = true
        }
    }

    // Atrás desde la vista diaria a la que se llegó pulsando un día → vuelve al modo anterior.
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                }
                Text(stringResource(R.string.my_calendar_title), style = MaterialTheme.typography.headlineMedium)
            }
            SyncStatusBanner(syncStatus)
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
            SpaceFilters(
                spaces = spaces,
                selectedIds = state.selectedSpaceIds,
                onToggle = viewModel::toggleSpace,
                onSelectAll = viewModel::selectAllSpaces,
                onClear = viewModel::clearSpaceSelection,
            )

            when (state.mode) {
                CalendarDisplayMode.MONTH -> MonthGrid(
                    month = YearMonth.from(state.focusedDate),
                    selected = state.focusedDate,
                    events = events.map(PersonalCalendarEvent::event),
                    select = { date ->
                        viewModel.select(date)
                        drilledFrom = state.mode
                        viewModel.setMode(CalendarDisplayMode.DAY)
                    },
                )
                CalendarDisplayMode.DAY -> DayHourGrid(
                    events = dayEvents.map(PersonalCalendarEvent::event),
                    tasks = emptyList(),
                    canWrite = canWrite,
                    onEdit = { event -> dayEventsById[event.id]?.let { openEditor(it.spaceId, it.event, null) } },
                    onDelete = { id -> dayEventsById[id]?.let { eventToDelete = it } },
                    onCreateAt = onCreateAt,
                    modifier = Modifier.weight(1f),
                )
                CalendarDisplayMode.WEEK -> PersonalWeekEventList(
                    events = events,
                    focusedDate = state.focusedDate,
                    onSelected = onEventSelected,
                    modifier = Modifier.weight(1f),
                )
            }

            state.message?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
                LaunchedEffect(message) { viewModel.clearMessage() }
            }
        }
        NewEventFab(canWrite = canWrite, spaceCount = spaces.size, onClick = { onCreateAt(null) })
    }

    if (spacePickerOpen) {
        SpacePickerDialog(
            spaces = spaces,
            onPick = { spaceId ->
                spacePickerOpen = false
                openEditor(spaceId, null, spacePickerTime)
            },
            onDismiss = { spacePickerOpen = false },
        )
    }

    CalendarEditorHost(
        editorEventId = editorEventId,
        events = events.takeIf { state.events is UiState.Content }
            ?.filter { it.spaceId == editorSpaceId }
            ?.map(PersonalCalendarEvent::event),
        creating = editorOpen && editorEventId == null,
        creatingAt = editorStartText?.let(LocalTime::parse),
        day = state.focusedDate,
        members = state.editorMembers,
        saving = state.saving,
        canWrite = canWrite,
        onClose = {
            editorOpen = false
            editorEventId = null
            editorStartText = null
            viewModel.clearEditorMembers()
        },
        onSave = { id, input -> editorSpaceId?.let { viewModel.saveEvent(it, id, input) } },
    )

    eventToDelete?.let { personal ->
        DeleteEventDialog(
            event = personal.event,
            enabled = canWrite && !state.saving,
            onConfirm = {
                viewModel.deleteEvent(personal.spaceId, personal.event.id)
                eventToDelete = null
            },
            onDismiss = { eventToDelete = null },
        )
    }
}

@Composable
private fun SpacePickerDialog(spaces: List<SpaceSummary>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.my_calendar_pick_space)) },
        text = {
            LazyColumn {
                items(spaces, key = SpaceSummary::id) { space ->
                    Text(
                        text = space.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(space.id) }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun SpaceFilters(
    spaces: List<SpaceSummary>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    Text(stringResource(R.string.my_calendar_spaces), style = MaterialTheme.typography.titleSmall)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { AssistChip(onClick = onSelectAll, label = { Text(stringResource(R.string.my_calendar_all_spaces)) }) }
        item { AssistChip(onClick = onClear, label = { Text(stringResource(R.string.my_calendar_none_spaces)) }) }
        items(spaces, key = SpaceSummary::id) { space ->
            FilterChip(
                selected = space.id in selectedIds,
                onClick = { onToggle(space.id) },
                label = { Text(space.name) },
            )
        }
    }
}

@Composable
private fun PersonalWeekEventList(
    events: List<PersonalCalendarEvent>,
    focusedDate: LocalDate,
    onSelected: (String, String, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monday = focusedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (0L..6L).forEach { offset ->
            val date = monday.plusDays(offset)
            item("header-$date") {
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEEE d")),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            val dayEvents = events.eventsOn(date)
            if (dayEvents.isEmpty()) {
                item("empty-$date") { Text(stringResource(R.string.calendar_no_events)) }
            } else {
                items(dayEvents, key = { "$date-${it.spaceId}-${it.event.id}" }) { item ->
                    PersonalEventRow(item) {
                        onSelected(item.spaceId, item.event.id, item.event.displayStartDate(ZoneId.systemDefault()))
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalEventRow(item: PersonalCalendarEvent, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.event.title, style = MaterialTheme.typography.titleMedium)
            Text(item.spaceName, color = MaterialTheme.colorScheme.primary)
            Text(
                if (item.event.allDay) {
                    stringResource(R.string.calendar_all_day)
                } else {
                    item.event.startAt.atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                },
            )
        }
    }
}

private fun List<PersonalCalendarEvent>.eventsOn(date: LocalDate): List<PersonalCalendarEvent> {
    val zone = ZoneId.systemDefault()
    val interval = date.visibleInterval(CalendarDisplayMode.DAY, zone)
    return filter { it.event.overlaps(interval.start, interval.endExclusive) }
        .sortedBy { it.event.startAt }
}

// Sin conexión o sin espacios no hay dónde crear el evento: no se muestra.
@Composable
private fun BoxScope.NewEventFab(canWrite: Boolean, spaceCount: Int, onClick: () -> Unit) {
    if (!canWrite || spaceCount == 0) return
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.calendar_new_event)) },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp),
    )
}
