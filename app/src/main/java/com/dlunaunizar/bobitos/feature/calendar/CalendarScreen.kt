package com.dlunaunizar.bobitos.feature.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.data.repository.EventInput
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
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
    var editor by remember { mutableStateOf<CalendarEvent?>(null) }
    var creating by remember { mutableStateOf(false) }
    var handledInitialEvent by rememberSaveable(initialEventId) { mutableStateOf(false) }

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

    LaunchedEffect(initialEventId, events) {
        if (initialEventId != null && !handledInitialEvent) {
            events.firstOrNull { it.id == initialEventId }?.let {
                editor = it
                handledInitialEvent = true
            }
        }
    }

    Column(
        modifier = modifier
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
        CalendarModeSelector(state.mode, viewModel::setMode)
        MemberFilters(
            members = members,
            selectedIds = state.selectedMemberIds,
            onToggle = viewModel::toggleMember,
            onSelectAll = viewModel::selectAllMembers,
            onClear = viewModel::clearMemberSelection,
        )
        Button(enabled = canWrite, onClick = { creating = true }) {
            Text("Nuevo evento")
        }

        when (state.mode) {
            CalendarDisplayMode.MONTH -> {
                MonthGrid(
                    month = YearMonth.from(state.focusedDate),
                    selected = state.focusedDate,
                    events = filteredEvents,
                    select = viewModel::select,
                )
                Text(
                    text = state.focusedDate.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                EventList(
                    events = filteredEvents.eventsOn(state.focusedDate),
                    canWrite = canWrite,
                    onEdit = { editor = it },
                    onDelete = viewModel::delete,
                    modifier = Modifier.weight(1f),
                )
            }
            CalendarDisplayMode.DAY -> EventList(
                events = filteredEvents.eventsOn(state.focusedDate),
                canWrite = canWrite,
                onEdit = { editor = it },
                onDelete = viewModel::delete,
                modifier = Modifier.weight(1f),
            )
            CalendarDisplayMode.WEEK -> WeekEventList(
                focusedDate = state.focusedDate,
                events = filteredEvents,
                canWrite = canWrite,
                onEdit = { editor = it },
                onDelete = viewModel::delete,
                modifier = Modifier.weight(1f),
            )
        }

        state.message?.let { message ->
            Text(message)
            LaunchedEffect(message) { viewModel.clearMessage() }
        }
    }

    if (creating || editor != null) {
        EventEditor(
            event = editor,
            day = state.focusedDate,
            members = members,
            saving = state.saving,
            canWrite = canWrite,
            dismiss = {
                creating = false
                editor = null
            },
        ) { id, input ->
            viewModel.save(id, input)
            creating = false
            editor = null
        }
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
    ) {
        TextButton(onClick = onPrevious) { Text("‹") }
        Text(label, style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onNext) { Text("›") }
    }
}

@Composable
internal fun CalendarModeSelector(
    selected: CalendarDisplayMode,
    onSelected: (CalendarDisplayMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CalendarDisplayMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                label = {
                    Text(
                        when (mode) {
                            CalendarDisplayMode.DAY -> "Día"
                            CalendarDisplayMode.WEEK -> "Semana"
                            CalendarDisplayMode.MONTH -> "Mes"
                        },
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
    Text("Personas", style = MaterialTheme.typography.titleSmall)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { AssistChip(onClick = onSelectAll, label = { Text("Todas") }) }
        item { AssistChip(onClick = onClear, label = { Text("Ninguna") }) }
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
    Column {
        Row {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach {
                Text(it, Modifier.weight(1f))
            }
        }
        (0L until 42L).map(first::plusDays).chunked(7).forEach { week ->
            Row {
                week.forEach { date ->
                    val count = events.eventsOn(date).size
                    Text(
                        text = "${date.dayOfMonth}${if (count > 0) " •$count" else ""}",
                        modifier = Modifier
                            .weight(1f)
                            .clickable { select(date) }
                            .padding(7.dp),
                        color = if (date == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        },
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
                item("empty-$date") { Text("Sin eventos") }
            } else {
                items(dayEvents, key = { "$date-${it.id}" }) { event ->
                    EventRow(event, canWrite, { onEdit(event) }, { onDelete(event.id) })
                }
            }
        }
    }
}

@Composable
private fun EventList(
    events: List<CalendarEvent>,
    canWrite: Boolean,
    onEdit: (CalendarEvent) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (events.isEmpty()) item { Text("Sin eventos") }
        items(events, key = CalendarEvent::id) { event ->
            EventRow(event, canWrite, { onEdit(event) }, { onDelete(event.id) })
        }
    }
}

@Composable
private fun EventRow(
    event: CalendarEvent,
    canWrite: Boolean,
    edit: () -> Unit,
    delete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(enabled = canWrite, onClick = edit)) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(event.title)
                Text(
                    if (event.allDay) "Todo el día"
                    else event.startAt.atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("dd/MM HH:mm")),
                )
                if (event.participantNames.isNotEmpty()) {
                    Text(event.participantNames.joinToString())
                } else {
                    Text("Evento general")
                }
            }
            TextButton(enabled = canWrite, onClick = delete) { Text("Eliminar") }
        }
    }
}

internal fun List<CalendarEvent>.eventsOn(date: LocalDate): List<CalendarEvent> {
    val zone = ZoneId.systemDefault()
    val interval = date.visibleInterval(CalendarDisplayMode.DAY, zone)
    return filter { it.overlaps(interval.start, interval.endExclusive) }.sortedBy { it.startAt }
}

@Composable
private fun EventEditor(
    event: CalendarEvent?,
    day: LocalDate,
    members: List<SpaceMember>,
    saving: Boolean,
    canWrite: Boolean,
    dismiss: () -> Unit,
    save: (String?, EventInput) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    var title by remember { mutableStateOf(event?.title.orEmpty()) }
    var description by remember { mutableStateOf(event?.description.orEmpty()) }
    var allDay by remember { mutableStateOf(event?.allDay ?: true) }
    var start by remember {
        mutableStateOf(
            event?.let {
                if (it.allDay) it.startDate.toString()
                else it.startAt.atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            } ?: day.toString(),
        )
    }
    var end by remember {
        mutableStateOf(
            event?.let {
                if (it.allDay) it.endDateExclusive!!.minusDays(1).toString()
                else it.endAt.atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            } ?: day.toString(),
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var color by remember { mutableStateOf(event?.color ?: EventColor.BLUE) }
    var selected by remember { mutableStateOf(event?.participantIds?.toSet().orEmpty()) }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (event == null) "Nuevo evento" else "Editar evento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") })
                OutlinedTextField(
                    description,
                    { description = it },
                    label = { Text("Descripción") },
                )
                Row {
                    Checkbox(allDay, { allDay = it })
                    Text("Todo el día")
                }
                OutlinedTextField(
                    start,
                    { start = it },
                    label = { Text(if (allDay) "Inicio AAAA-MM-DD" else "Inicio AAAA-MM-DD HH:mm") },
                )
                OutlinedTextField(
                    end,
                    { end = it },
                    label = { Text(if (allDay) "Fin AAAA-MM-DD" else "Fin AAAA-MM-DD HH:mm") },
                )
                Text("Color")
                Row {
                    EventColor.entries.forEach {
                        Text(
                            it.name.take(1),
                            Modifier.clickable { color = it }.padding(5.dp),
                            color = if (it == color) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
                        )
                    }
                }
                if (members.isNotEmpty()) Text("Participantes opcionales")
                members.forEach { member ->
                    Row {
                        Checkbox(
                            member.userId in selected,
                            { checked ->
                                selected = if (checked) selected + member.userId
                                else selected - member.userId
                            },
                        )
                        Text(member.displayName)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = canWrite && !saving,
                onClick = {
                    try {
                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        val startDate = if (allDay) LocalDate.parse(start) else null
                        val endDate = if (allDay) LocalDate.parse(end).plusDays(1) else null
                        val startInstant = if (allDay) startDate!!.atStartOfDay(zone).toInstant()
                        else parseLocal(start, formatter, zone)
                        val endInstant = if (allDay) endDate!!.atStartOfDay(zone).toInstant()
                        else parseLocal(end, formatter, zone)
                        save(
                            event?.id,
                            EventInput(
                                title,
                                description,
                                allDay,
                                startInstant,
                                endInstant,
                                startDate,
                                endDate,
                                zone.id,
                                color,
                                selected.toList(),
                            ),
                        )
                    } catch (_: Exception) {
                        error = "Revisa las fechas y el intervalo"
                    }
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } },
    )
}

private fun parseLocal(
    value: String,
    formatter: DateTimeFormatter,
    zone: ZoneId,
): Instant {
    val local = LocalDateTime.parse(value, formatter)
    val offsets = zone.rules.getValidOffsets(local)
    require(offsets.isNotEmpty())
    return local.atOffset(offsets.first()).toInstant()
}
