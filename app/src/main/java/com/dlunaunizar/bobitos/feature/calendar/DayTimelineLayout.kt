package com.dlunaunizar.bobitos.feature.calendar

import com.dlunaunizar.bobitos.core.model.CalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal const val MINUTES_IN_DAY = 1440

/** Alto mínimo (en minutos) de la tarjeta de un evento, para que un evento corto siga siendo legible. */
internal const val MIN_VISUAL_MINUTES = 45

/**
 * Un evento colocado en la línea de tiempo de un día. [topMinute] y [bottomMinute] son minutos desde las 00:00,
 * ya recortados al día y con el mínimo aplicado (`0 ≤ topMinute < bottomMinute ≤ 1440`); [column] va de `0` a
 * `columns - 1` entre los eventos solapados del mismo grupo.
 */
internal data class TimelineEvent(
    val event: CalendarEvent,
    val topMinute: Int,
    val bottomMinute: Int,
    val column: Int,
    val columns: Int,
)

/**
 * Coloca los eventos con hora que tocan [day]: descarta los de todo el día y los que no lo solapan. Devuelve la lista
 * en orden de inicio y, a igual inicio, del más largo al más corto.
 */
internal fun layoutDayEvents(events: List<CalendarEvent>, day: LocalDate, zone: ZoneId): List<TimelineEvent> {
    val interval = day.visibleInterval(CalendarDisplayMode.DAY, zone)
    val spans = events
        .filter { !it.allDay && it.overlaps(interval.start, interval.endExclusive) }
        .map { event ->
            val top = minuteInDay(event.startAt, interval, zone)
            val bottom = minuteInDay(event.endAt, interval, zone)
            val (fixedTop, fixedBottom) = withMinimum(top, bottom)
            Span(event, fixedTop, fixedBottom)
        }
        .sortedWith(compareBy<Span> { it.top }.thenByDescending { it.bottom }.thenBy { it.event.id })
    return assignColumns(spans)
}

/** La hora en punto del primer evento con hora de [day], o `null` si no hay ninguno. */
internal fun firstTimedHour(events: List<CalendarEvent>, day: LocalDate, zone: ZoneId): Int? =
    layoutDayEvents(events, day, zone).firstOrNull()?.let { it.topMinute / 60 }

private class Span(val event: CalendarEvent, val top: Int, val bottom: Int)

private fun minuteInDay(instant: Instant, interval: CalendarInterval, zone: ZoneId): Int = when {
    instant < interval.start -> 0
    instant >= interval.endExclusive -> MINUTES_IN_DAY
    else -> instant.atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
}

private fun withMinimum(top: Int, bottom: Int): Pair<Int, Int> {
    val minimumBottom = maxOf(bottom, top + MIN_VISUAL_MINUTES)
    return if (minimumBottom > MINUTES_IN_DAY) {
        (MINUTES_IN_DAY - MIN_VISUAL_MINUTES) to MINUTES_IN_DAY
    } else {
        top to minimumBottom
    }
}

/** Reparte los [spans] (ya ordenados) en columnas dentro de cada grupo de eventos encadenados por solapes. */
private fun assignColumns(spans: List<Span>): List<TimelineEvent> {
    val result = mutableListOf<TimelineEvent>()
    var group = mutableListOf<Pair<Span, Int>>()
    val columnEnds = mutableListOf<Int>()
    var groupEnd = 0

    fun closeGroup() {
        group.forEach { (span, column) ->
            result += TimelineEvent(span.event, span.top, span.bottom, column, columnEnds.size)
        }
        group = mutableListOf()
        columnEnds.clear()
    }

    spans.forEach { span ->
        if (group.isNotEmpty() && span.top >= groupEnd) closeGroup()
        val free = columnEnds.indexOfFirst { it <= span.top }
        val column = if (free >= 0) free else columnEnds.size.also { columnEnds += 0 }
        columnEnds[column] = span.bottom
        group += span to column
        groupEnd = maxOf(groupEnd, span.bottom)
    }
    closeGroup()
    return result
}
