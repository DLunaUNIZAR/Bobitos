package com.dlunaunizar.bobitos.feature.calendar

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import java.time.LocalDate

private val expandedRunsSaver: Saver<MutableState<Set<LocalDate>>, List<Long>> = Saver(
    save = { state -> state.value.map(LocalDate::toEpochDay) },
    restore = { days -> mutableStateOf(days.map(LocalDate::ofEpochDay).toSet()) },
)

/** Inicios de los grupos de días vacíos desplegados; se reinicia al cambiar de semana. */
@Composable
internal fun rememberExpandedRuns(weekStart: LocalDate): MutableState<Set<LocalDate>> =
    rememberSaveable(weekStart, saver = expandedRunsSaver) { mutableStateOf(emptySet()) }

/** Una fila de la vista Semana: un día con su cabecera o un grupo de días vacíos seguidos. */
sealed interface WeekRow {
    data class Day(val date: LocalDate) : WeekRow

    data class EmptyRun(val start: LocalDate, val end: LocalDate) : WeekRow
}

/** Junta los días vacíos consecutivos en un [WeekRow.EmptyRun]. Hoy nunca se pliega. */
internal fun weekRows(days: List<LocalDate>, daysWithEvents: Set<LocalDate>, today: LocalDate): List<WeekRow> {
    val rows = mutableListOf<WeekRow>()
    var runStart: LocalDate? = null
    var runEnd: LocalDate? = null
    fun closeRun() {
        val start = runStart
        val end = runEnd
        if (start != null && end != null) rows += WeekRow.EmptyRun(start, end)
        runStart = null
        runEnd = null
    }
    for (date in days) {
        if (date in daysWithEvents || date == today) {
            closeRun()
            rows += WeekRow.Day(date)
        } else {
            if (runStart == null) runStart = date
            runEnd = date
        }
    }
    closeRun()
    return rows
}

/** Filas de la semana que empieza en [monday]; se recalculan cuando cambian [monday], [events] o [today]. */
@Composable
internal fun rememberWeekRows(
    monday: LocalDate,
    events: Any?,
    today: LocalDate,
    hasEvents: (LocalDate) -> Boolean,
): List<WeekRow> = remember(monday, events, today) {
    val days = (0L..6L).map(monday::plusDays)
    weekRows(days, days.filterTo(mutableSetOf(), hasEvents), today)
}

/** Añade las filas de la semana: cada día con [dayItems] y los grupos de días vacíos plegables. */
internal fun LazyListScope.weekRowItems(
    rows: List<WeekRow>,
    expandedRuns: Set<LocalDate>,
    onToggle: (LocalDate) -> Unit,
    dayItems: LazyListScope.(LocalDate) -> Unit,
) {
    rows.forEach { row ->
        when (row) {
            is WeekRow.Day -> dayItems(row.date)
            is WeekRow.EmptyRun -> emptyRunItems(row, row.start in expandedRuns, onToggle, dayItems)
        }
    }
}

private fun LazyListScope.emptyRunItems(
    run: WeekRow.EmptyRun,
    open: Boolean,
    onToggle: (LocalDate) -> Unit,
    dayItems: LazyListScope.(LocalDate) -> Unit,
) {
    item("run-${run.start}") {
        FoldedDaysRow(start = run.start, end = run.end, expanded = open, onToggle = { onToggle(run.start) })
    }
    if (open) {
        generateSequence(run.start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(run.end) }
            .forEach { dayItems(it) }
    }
}
