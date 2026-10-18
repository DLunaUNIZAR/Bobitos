package com.dlunaunizar.bobitos.feature.spaces

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class DayPeriod { MORNING, AFTERNOON, EVENING }

// Horas en que empieza cada periodo: mañana a las 5, tarde a las 12 y noche a las 20.
private val PERIOD_START_HOURS = listOf(5, 12, 20)

fun dayPeriodFor(hour: Int): DayPeriod = when (hour) {
    in 5..11 -> DayPeriod.MORNING
    in 12..19 -> DayPeriod.AFTERNOON
    else -> DayPeriod.EVENING
}

fun firstName(displayName: String): String = displayName.trim().substringBefore(' ')

/** Milisegundos que faltan para el próximo cambio de periodo del día en la zona de [now]. */
internal fun millisUntilNextDayPeriod(now: ZonedDateTime): Long {
    val today = now.toLocalDate()
    val next = PERIOD_START_HOURS.map { today.atTime(it, 0).atZone(now.zone) }.firstOrNull { it.isAfter(now) }
        ?: today.plusDays(1).atTime(PERIOD_START_HOURS.first(), 0).atZone(now.zone)
    return Duration.between(now, next).toMillis()
}

// Emite el periodo actual y de nuevo en cada cambio de periodo.
private fun dayPeriods(zone: ZoneId): Flow<DayPeriod> = flow {
    while (true) {
        val now = ZonedDateTime.now(zone)
        emit(dayPeriodFor(now.hour))
        // Un segundo de margen para no emitir antes del cambio.
        delay(millisUntilNextDayPeriod(now) + 1_000)
    }
}.distinctUntilChanged()

/**
 * El periodo del día (para el saludo). Vuelve a leerse al volver a la app y cambia solo en cada cambio de periodo,
 * como [com.dlunaunizar.bobitos.core.designsystem.rememberToday] con el día.
 */
@Composable
fun rememberDayPeriod(zone: ZoneId = ZoneId.systemDefault()): DayPeriod {
    val period by remember(zone) { dayPeriods(zone) }
        .collectAsStateWithLifecycle(initialValue = dayPeriodFor(LocalDateTime.now(zone).hour))
    return period
}
