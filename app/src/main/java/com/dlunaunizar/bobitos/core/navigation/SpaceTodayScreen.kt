package com.dlunaunizar.bobitos.core.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.BobitosDates
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.messageRes
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.GroupedRow
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.component.RowPosition
import com.dlunaunizar.bobitos.core.designsystem.component.SectionHeader
import com.dlunaunizar.bobitos.core.designsystem.component.rowPosition
import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.designsystem.theme.moduleColors
import com.dlunaunizar.bobitos.core.designsystem.theme.moduleTint
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.feature.meals.labelRes
import com.dlunaunizar.bobitos.feature.spaces.AgendaEntry
import com.dlunaunizar.bobitos.feature.spaces.AgendaPerson
import com.dlunaunizar.bobitos.feature.spaces.AgendaTask
import com.dlunaunizar.bobitos.feature.spaces.AgendaTime
import com.dlunaunizar.bobitos.feature.spaces.ShoppingGlance
import com.dlunaunizar.bobitos.feature.spaces.TodayAgenda
import com.dlunaunizar.bobitos.feature.spaces.WorkloadSection
import com.dlunaunizar.bobitos.feature.tasks.label
import java.time.LocalDate
import java.time.format.TextStyle

private val TimeColumnWidth = 72.dp
private val FlagSize = 16.dp

/**
 * «Hoy»: la agenda de toda la casa. Tareas que vencen (con casilla para completarlas sin salir), lo de hoy en el
 * calendario, las comidas y el deporte, la compra pendiente y el reparto. El título con la fecha y el saludo los
 * pone el marco.
 */
@Composable
internal fun SpaceTodayScreen(
    agenda: UiState<TodayAgenda>,
    canWrite: Boolean,
    onToggleTask: (TaskItem) -> Unit,
    onRetry: () -> Unit,
    onOpen: (BobitosDestination) -> Unit,
) {
    when (agenda) {
        UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
        is UiState.Error -> ErrorState(
            modifier = Modifier.fillMaxSize(),
            message = stringResource(agenda.failure.messageRes()),
            onRetry = onRetry,
        )
        is UiState.Content -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            val today = agenda.value
            TasksSection(today, canWrite, onToggleTask, onOpen)
            AgendaSection(today.entries, onOpen)
            ShoppingRow(today.shopping, onOpen)
            WorkloadSection(today.workload)
        }
    }
}

@Composable
private fun TasksSection(
    agenda: TodayAgenda,
    canWrite: Boolean,
    onToggleTask: (TaskItem) -> Unit,
    onOpen: (BobitosDestination) -> Unit,
) {
    if (agenda.tasks.isEmpty()) return
    val total = agenda.tasks.size + agenda.hiddenTasks
    val rows = agenda.tasks.size + if (agenda.hiddenTasks > 0) 1 else 0
    // Se fija con la agenda: así «ayer» sigue bien tras la medianoche, cuando llega una agenda nueva.
    val today = remember(agenda) { LocalDate.now() }
    Column {
        SectionHeader(title = stringResource(R.string.agenda_tasks_title), count = total)
        agenda.tasks.forEachIndexed { index, item ->
            TaskRow(
                item = item,
                today = today,
                position = rowPosition(index, rows),
                canWrite = canWrite,
                onToggle = { onToggleTask(item.task) },
                onOpen = { onOpen(BobitosDestination.Tasks) },
            )
        }
        if (agenda.hiddenTasks > 0) {
            GroupedRow(
                position = rowPosition(rows - 1, rows),
                onClick = { onOpen(BobitosDestination.Tasks) },
            ) {
                Text(
                    text = stringResource(R.string.agenda_tasks_more, total),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).padding(start = Spacing.xs),
                )
                RowArrow()
            }
        }
    }
}

@Composable
private fun TaskRow(
    item: AgendaTask,
    today: LocalDate,
    position: RowPosition,
    canWrite: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val accent = moduleColors(AppModule.TASKS).accent
    GroupedRow(position = position, tint = moduleTint(AppModule.TASKS), onClick = onOpen) {
        // Su propio destino de 48 dp: completa sin salir de Hoy; el resto de la fila lleva a Tareas.
        Checkbox(
            checked = false,
            onCheckedChange = { onToggle() },
            enabled = canWrite,
            modifier = Modifier.semantics { contentDescription = item.task.title },
            colors = CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = accent),
        )
        Column(Modifier.weight(1f).padding(start = Spacing.xs)) {
            Text(item.task.title, style = MaterialTheme.typography.bodyLarge)
            TaskMeta(item, today)
        }
    }
}

// «Venció ayer · Alta · Tú», con la bandera delante; lo vencido, en el color de error.
@Composable
private fun TaskMeta(item: AgendaTask, today: LocalDate) {
    val error = MaterialTheme.colorScheme.error
    val overdue = item.overdueSince?.let { overdueText(it, today) }
    val rest = listOf(item.task.priority.label(), item.owner.label()).joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.Flag,
            contentDescription = null,
            tint = moduleColors(AppModule.TASKS).accent,
            modifier = Modifier.padding(end = Spacing.xs).size(FlagSize),
        )
        Text(
            text = buildAnnotatedString {
                if (overdue != null) {
                    withStyle(SpanStyle(color = error)) { append(overdue) }
                    append(" · ")
                }
                append(rest)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun overdueText(since: LocalDate, today: LocalDate): String = if (since == today.minusDays(1)) {
    stringResource(R.string.agenda_overdue_yesterday)
} else {
    val month = since.month.getDisplayName(TextStyle.FULL, BobitosDates.locale)
    stringResource(R.string.agenda_overdue_date, "${since.dayOfMonth} de $month")
}

@Composable
private fun AgendaSection(entries: List<AgendaEntry>, onOpen: (BobitosDestination) -> Unit) {
    // Las franjas sin planificar salen siempre; si no hay nada más, se dice.
    val empty = entries.all { it is AgendaEntry.Meal && it.meal == null }
    Column {
        SectionHeader(title = stringResource(R.string.agenda_title))
        if (empty) {
            Text(
                text = stringResource(R.string.agenda_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }
        entries.forEachIndexed { index, entry ->
            AgendaRow(entry, rowPosition(index, entries.size), onOpen)
        }
    }
}

// Lo que pinta una fila de la agenda; [spoken] es la línea secundaria tal como la lee TalkBack.
private data class AgendaRowText(val time: String, val title: String, val secondary: List<String>, val spoken: String)

@Composable
private fun AgendaRow(entry: AgendaEntry, position: RowPosition, onOpen: (BobitosDestination) -> Unit) {
    val destination = entry.destination()
    val module = destination.appModule()
    val unplanned = entry is AgendaEntry.Meal && entry.meal == null
    val text = agendaRowText(entry)
    val description = if (text.spoken.isEmpty()) {
        stringResource(R.string.agenda_row_description, text.time, text.title)
    } else {
        stringResource(R.string.agenda_row_description_with_who, text.time, text.title, text.spoken)
    }
    val dimmed = MaterialTheme.colorScheme.onSurfaceVariant
    GroupedRow(
        position = position,
        // La franja vacía es neutra y de texto atenuado: invita a planificar sin competir con lo que hay.
        tint = if (unplanned) null else module?.let { moduleTint(it) },
        onClick = { onOpen(destination) },
    ) {
        Row(
            modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text.time,
                style = MaterialTheme.typography.labelMedium,
                color = dimmed,
                modifier = Modifier.width(TimeColumnWidth).padding(end = Spacing.sm),
            )
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = if (unplanned || module == null) dimmed else moduleColors(module).accent,
                modifier = Modifier.padding(end = Spacing.md),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = text.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (unplanned) dimmed else MaterialTheme.colorScheme.onSurface,
                )
                if (text.secondary.isNotEmpty()) {
                    Text(
                        text = text.secondary.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = dimmed,
                    )
                }
            }
        }
    }
}

private fun AgendaEntry.destination(): BobitosDestination = when (this) {
    is AgendaEntry.Event -> BobitosDestination.Calendar
    is AgendaEntry.Meal -> BobitosDestination.Meals
    is AgendaEntry.Activity -> BobitosDestination.Sport
}

@Composable
private fun agendaRowText(entry: AgendaEntry): AgendaRowText {
    val time = entry.time.label()
    val done = stringResource(R.string.agenda_done)
    return when (entry) {
        is AgendaEntry.Event -> entry.people.labels().let {
            AgendaRowText(time, entry.event.title, it, it.joinToString())
        }
        is AgendaEntry.Activity -> (entry.people.labels() + listOfNotNull(done.takeIf { entry.activity.done })).let {
            AgendaRowText(time, entry.activity.name, it, it.joinToString())
        }
        is AgendaEntry.Meal -> mealRowText(entry, time)
    }
}

@Composable
private fun mealRowText(entry: AgendaEntry.Meal, time: String): AgendaRowText {
    val meal = entry.meal ?: return AgendaRowText(time, stringResource(R.string.meals_slot_empty), emptyList(), "")
    val cook = when (val cook = entry.cook) {
        null -> null
        AgendaPerson.You -> stringResource(R.string.agenda_cook_you)
        else -> stringResource(R.string.meals_cook_value, cook.label())
    }
    val eaters = if (entry.eaters > 0) {
        pluralStringResource(R.plurals.agenda_eaters, entry.eaters, entry.eaters)
    } else {
        null
    }
    val secondary = listOfNotNull(cook, eaters)
    // Hablado, «cocinas tú» va en minúscula tras los dos puntos y el título: «Comida: Albóndigas, cocinas tú».
    val spoken = listOfNotNull(cook?.replaceFirstChar { it.lowercase(BobitosDates.locale) }, eaters)
    return AgendaRowText(time, meal.name, secondary, spoken.joinToString())
}

@Composable
private fun AgendaTime.label(): String = when (this) {
    AgendaTime.AllDay -> stringResource(R.string.calendar_all_day)
    AgendaTime.NoTime -> stringResource(R.string.agenda_time_no_time)
    AgendaTime.Continues -> stringResource(R.string.agenda_time_continues)
    is AgendaTime.At -> BobitosDates.time(time)
    is AgendaTime.Slot -> stringResource(slot.labelRes)
}

@Composable
private fun AgendaPerson.label(): String = when (this) {
    AgendaPerson.You -> stringResource(R.string.agenda_you)
    is AgendaPerson.Member -> name
    AgendaPerson.Nobody -> stringResource(R.string.agenda_unassigned)
}

@Composable
private fun List<AgendaPerson>.labels(): List<String> = map { it.label() }

@Composable
private fun ShoppingRow(shopping: ShoppingGlance, onOpen: (BobitosDestination) -> Unit) {
    val destination = BobitosDestination.Shopping
    val accent = moduleColors(AppModule.SHOPPING).accent
    GroupedRow(
        position = RowPosition.SINGLE,
        tint = moduleTint(AppModule.SHOPPING),
        onClick = { onOpen(destination) },
    ) {
        Icon(destination.icon, contentDescription = null, tint = accent)
        Column(Modifier.weight(1f).padding(start = Spacing.md)) {
            if (shopping.pending == 0) {
                Text(stringResource(R.string.agenda_shopping_empty), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    text = stringResource(
                        R.string.section_header_count,
                        stringResource(destination.titleRes),
                        shopping.pending,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = shoppingNames(shopping),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        RowArrow()
    }
}

// «Leche, pan, huevos y 2 más»; sin resto, «Leche, pan y huevos».
@Composable
private fun shoppingNames(shopping: ShoppingGlance): String {
    val names = shopping.firstNames
    val more = shopping.pending - names.size
    return when {
        names.isEmpty() -> ""
        more > 0 -> stringResource(R.string.agenda_shopping_more, names.joinToString(", "), more)
        names.size == 1 -> names.first()
        else -> stringResource(R.string.agenda_list_and, names.dropLast(1).joinToString(", "), names.last())
    }
}
