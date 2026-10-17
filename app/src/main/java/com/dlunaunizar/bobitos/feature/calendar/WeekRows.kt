package com.dlunaunizar.bobitos.feature.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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
