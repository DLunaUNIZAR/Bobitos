package com.dlunaunizar.bobitos.feature.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.BobitosDates
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.messageRes
import com.dlunaunizar.bobitos.core.designsystem.component.AddFab
import com.dlunaunizar.bobitos.core.designsystem.component.EditorSaveEffect
import com.dlunaunizar.bobitos.core.designsystem.component.ModuleHeaderBand
import com.dlunaunizar.bobitos.core.designsystem.component.SaveFeedbackEffect
import com.dlunaunizar.bobitos.core.designsystem.component.ScreenScaffold
import com.dlunaunizar.bobitos.core.designsystem.component.UndoOfferEffect
import com.dlunaunizar.bobitos.core.designsystem.rememberFabExpanded
import com.dlunaunizar.bobitos.core.designsystem.rememberToday
import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.core.model.SyncStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
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
    val undoOffers by viewModel.undoOffers.collectAsStateWithLifecycle()

    val loadedEvents = (state.events as? UiState.Content)?.value
    val events = loadedEvents.orEmpty().forSelectedSpaces(state.selectedSpaceIds)
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
    val onBackAction: () -> Unit = {
        applyBackAction(
            action = personalCalendarBackAction(drilledFrom),
            onRestoreMode = {
                viewModel.setMode(it)
                drilledFrom = null
            },
            onLeave = { onBack?.invoke() },
        )
    }
    BackHandler(enabled = drilledFrom != null, onBack = onBackAction)

    // El FAB sigue la lista de la vista abierta; el mes no se desplaza.
    val dayListState = rememberLazyListState()
    val weekListState = rememberLazyListState()
    val monthListState = rememberLazyListState()
    val fabListState = when (state.mode) {
        CalendarDisplayMode.DAY -> dayListState
        CalendarDisplayMode.WEEK -> weekListState
        CalendarDisplayMode.MONTH -> monthListState
    }
    ScreenScaffold(
        module = AppModule.CALENDAR,
        title = stringResource(R.string.my_calendar_title),
        onBack = onBack?.let { onBackAction },
        modifier = modifier,
        syncStatus = syncStatus,
        floatingActionButton = {
            NewEventFab(
                canWrite = canWrite,
                spaceCount = spaces.size,
                expanded = rememberFabExpanded(fabListState),
                onClick = { onCreateAt(null) },
            )
        },
    ) { padding ->
        // El espacio se captura al borrar: «Deshacer» devuelve el evento a su espacio.
        val deleteWithUndo: (PersonalCalendarEvent) -> Unit = { personal ->
            viewModel.deleteEvent(personal.spaceId, personal.event)
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Periodo, vista y espacios fijos en la franja: el calendario es navegación, no se desplaza.
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
                    SpacesFilter(
                        spaces = spaces,
                        selectedIds = state.selectedSpaceIds,
                        onToggle = viewModel::toggleSpace,
                        onSelectAll = viewModel::selectAllSpaces,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EventsLoadState(state.events, state.refreshing, viewModel::retryEvents, Modifier.weight(1f)) {
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
                            onEdit = { event ->
                                dayEventsById[event.id]?.let { openEditor(it.spaceId, it.event, null) }
                            },
                            onDelete = { id -> dayEventsById[id]?.let(deleteWithUndo) },
                            onCreateAt = onCreateAt,
                            modifier = Modifier.weight(1f),
                            deleteEnabled = !state.save.isSaving,
                            listState = dayListState,
                        )
                        CalendarDisplayMode.WEEK -> PersonalWeekEventList(
                            events = events,
                            focusedDate = state.focusedDate,
                            onSelected = onEventSelected,
                            modifier = Modifier.weight(1f),
                            listState = weekListState,
                        )
                    }
                }

                SaveFeedbackEffect(
                    save = state.save,
                    controls = viewModel.feedback,
                    covered = editorOpen,
                    errorText = { stringResource(it.messageRes()) },
                    noticeText = { "" },
                )
                UndoOfferEffect(
                    offers = undoOffers,
                    controls = viewModel.undoControls,
                    deletedMessage = R.string.calendar_undo_deleted,
                    onUndo = viewModel::undo,
                )
            }
        }
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

    val closeEditor = {
        // Cerrar tras un guardado fallido descarta ese error (ya se veía en la hoja).
        viewModel.feedback.resetEditor()
        editorOpen = false
        editorEventId = null
        editorStartText = null
        viewModel.clearEditorMembers()
    }
    EditorSaveEffect(
        editor = state.save.editor,
        editorOpen = editorOpen,
        controls = viewModel.feedback,
        onClose = closeEditor,
    )
    CalendarEditorHost(
        editorEventId = editorEventId,
        events = loadedEvents
            ?.filter { it.spaceId == editorSpaceId }
            ?.map(PersonalCalendarEvent::event),
        creating = editorOpen && editorEventId == null,
        creatingAt = editorStartText?.let(LocalTime::parse),
        day = state.focusedDate,
        members = state.editorMembers,
        saving = state.save.isSaving,
        errorMessage = state.save.editorError?.let { stringResource(it.messageRes()) },
        canWrite = canWrite,
        onClose = closeEditor,
        onSave = { id, input -> editorSpaceId?.let { viewModel.saveEvent(it, id, input) } },
    )
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
internal fun SpacesFilter(
    spaces: List<SpaceSummary>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MultiFilter(
        label = stringResource(R.string.my_calendar_spaces),
        allText = stringResource(R.string.my_calendar_all_spaces),
        countPlural = R.plurals.my_calendar_spaces_count,
        items = spaces.map { it.id to it.name },
        selectedIds = selectedIds,
        onToggle = onToggle,
        onSelectAll = onSelectAll,
        tag = "filtro_espacios",
        modifier = modifier,
    )
}

@Composable
private fun PersonalWeekEventList(
    events: List<PersonalCalendarEvent>,
    focusedDate: LocalDate,
    onSelected: (String, String, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val monday = focusedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val rows = rememberWeekRows(monday, events, rememberToday()) { events.eventsOn(it).isNotEmpty() }
    var expandedRuns by rememberExpandedRuns(monday)
    LazyColumn(
        modifier,
        state = listState,
        contentPadding = PaddingValues(bottom = Spacing.fabClearance),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        fun LazyListScope.dayItems(date: LocalDate) {
            item("header-$date") {
                Text(
                    BobitosDates.dayHeader(date),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
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
private fun PersonalEventRow(item: PersonalCalendarEvent, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.event.title, style = MaterialTheme.typography.titleMedium)
            Text(item.spaceName, color = MaterialTheme.colorScheme.primary)
            Text(
                if (item.event.allDay) {
                    stringResource(R.string.calendar_all_day)
                } else {
                    BobitosDates.dateTime(item.event.startAt.atZone(ZoneId.systemDefault()).toLocalDateTime())
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
private fun NewEventFab(canWrite: Boolean, spaceCount: Int, expanded: Boolean, onClick: () -> Unit) {
    if (!canWrite || spaceCount == 0) return
    AddFab(
        label = R.string.calendar_new_event,
        expanded = expanded,
        onClick = onClick,
    )
}

/** Resultado de pulsar Atrás en Mi calendario: la flecha de la barra y el Atrás del sistema comparten esta decisión. */
internal sealed interface BackAction {
    data class ToPreviousMode(val mode: CalendarDisplayMode) : BackAction
    data object Leave : BackAction
}

private fun applyBackAction(action: BackAction, onRestoreMode: (CalendarDisplayMode) -> Unit, onLeave: () -> Unit) {
    when (action) {
        is BackAction.ToPreviousMode -> onRestoreMode(action.mode)
        BackAction.Leave -> onLeave()
    }
}

/** Con un día abierto desde otra vista se vuelve a ella; si no, se sale de la pantalla. */
internal fun personalCalendarBackAction(drilledFrom: CalendarDisplayMode?): BackAction =
    if (drilledFrom != null) BackAction.ToPreviousMode(drilledFrom) else BackAction.Leave
