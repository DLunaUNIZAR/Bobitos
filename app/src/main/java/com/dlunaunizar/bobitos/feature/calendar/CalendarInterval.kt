package com.dlunaunizar.bobitos.feature.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CalendarInterval(val start: Instant, val endExclusive: Instant)

fun YearMonth.visibleInterval(zoneId: ZoneId): CalendarInterval {
    val firstGridDate = atDay(1).minusDays(atDay(1).dayOfWeek.value.toLong() - 1)
    val lastMonthDate = atEndOfMonth()
    val lastGridDate = lastMonthDate.plusDays((7 - lastMonthDate.dayOfWeek.value).toLong())
    return CalendarInterval(
        firstGridDate.atStartOfDay(zoneId).toInstant(),
        lastGridDate.plusDays(1).atStartOfDay(zoneId).toInstant(),
    )
}

fun allDayInterval(start: LocalDate, endInclusive: LocalDate, zoneId: ZoneId): CalendarInterval {
    require(!endInclusive.isBefore(start))
    return CalendarInterval(
        start.atStartOfDay(zoneId).toInstant(),
        endInclusive.plusDays(1).atStartOfDay(zoneId).toInstant(),
    )
}
