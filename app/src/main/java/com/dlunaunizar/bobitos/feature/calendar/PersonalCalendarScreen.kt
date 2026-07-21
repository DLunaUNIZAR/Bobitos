package com.dlunaunizar.bobitos.feature.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.core.model.SyncStatus
import com.dlunaunizar.bobitos.feature.common.SyncStatusBanner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters

@Composable
fun PersonalCalendarScreen(
    userId: String,
    spaces: List<SpaceSummary>,
    syncStatus: SyncStatus,
    onEventSelected: (spaceId: String, eventId: String, date: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonalCalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(userId, spaces) { viewModel.observe(userId, spaces) }
    DisposableEffect(Unit) { onDispose(viewModel::stop) }
    var drilledFrom by remember { mutableStateOf<CalendarDisplayMode?>(null) }

    val events = (state.events as? UiState.Content)?.value.orEmpty()
        .filter { it.spaceId in state.selectedSpaceIds }

    // Atrás desde la vista diaria a la que se llegó pulsando un día → vuelve al modo anterior.
    BackHandler(enabled = drilledFrom != null) {
        drilledFrom?.let(viewModel::setMode)
        drilledFrom = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.my_calendar_title), style = MaterialTheme.typography.headlineMedium)
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
            CalendarDisplayMode.MONTH -> {
                MonthGrid(
                    month = YearMonth.from(state.focusedDate),
                    selected = state.focusedDate,
                    events = events.map(PersonalCalendarEvent::event),
                    select = { date ->
                        viewModel.select(date)
                        drilledFrom = state.mode
                        viewModel.setMode(CalendarDisplayMode.DAY)
                    },
                )
                Text(
                    state.focusedDate.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                PersonalEventList(
                    events = events.eventsOn(state.focusedDate),
                    onSelected = onEventSelected,
                    modifier = Modifier.weight(1f),
                )
            }
            CalendarDisplayMode.DAY -> PersonalEventList(
                events = events.eventsOn(state.focusedDate),
                onSelected = onEventSelected,
                modifier = Modifier.weight(1f),
            )
            CalendarDisplayMode.WEEK -> PersonalWeekEventList(
                events = events,
                focusedDate = state.focusedDate,
                onSelected = onEventSelected,
                modifier = Modifier.weight(1f),
            )
        }
    }
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
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
private fun PersonalEventList(
    events: List<PersonalCalendarEvent>,
    onSelected: (String, String, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (events.isEmpty()) item { Text(stringResource(R.string.my_calendar_empty)) }
        items(events, key = { "${it.spaceId}-${it.event.id}" }) { item ->
            PersonalEventRow(item) {
                onSelected(item.spaceId, item.event.id, item.event.displayStartDate(ZoneId.systemDefault()))
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
