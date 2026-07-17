package com.dlunaunizar.bobitos.feature.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class CalendarInterval(val start: Instant, val endExclusive: Instant)

enum class CalendarDisplayMode {
    DAY,
    WEEK,
    MONTH,
}

fun LocalDate.visibleInterval(mode: CalendarDisplayMode, zoneId: ZoneId): CalendarInterval = when (mode) {
    CalendarDisplayMode.DAY -> CalendarInterval(
        atStartOfDay(zoneId).toInstant(),
        plusDays(1).atStartOfDay(zoneId).toInstant(),
    )
    CalendarDisplayMode.WEEK -> {
        val monday = with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        CalendarInterval(
            monday.atStartOfDay(zoneId).toInstant(),
            monday.plusWeeks(1).atStartOfDay(zoneId).toInstant(),
        )
    }
    CalendarDisplayMode.MONTH -> YearMonth.from(this).visibleInterval(zoneId)
}

fun LocalDate.move(mode: CalendarDisplayMode, amount: Long): LocalDate = when (mode) {
    CalendarDisplayMode.DAY -> plusDays(amount)
    CalendarDisplayMode.WEEK -> plusWeeks(amount)
    CalendarDisplayMode.MONTH -> plusMonths(amount).withDayOfMonth(1)
}

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
