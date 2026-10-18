package com.dlunaunizar.bobitos.feature.spaces

enum class DayPeriod { MORNING, AFTERNOON, EVENING }

fun dayPeriodFor(hour: Int): DayPeriod = when (hour) {
    in 5..11 -> DayPeriod.MORNING
    in 12..19 -> DayPeriod.AFTERNOON
    else -> DayPeriod.EVENING
}

fun firstName(displayName: String): String = displayName.trim().substringBefore(' ')
