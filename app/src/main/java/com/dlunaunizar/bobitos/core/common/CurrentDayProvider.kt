package com.dlunaunizar.bobitos.core.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/** Fuente del día actual: emite hoy al empezar y de nuevo en cada medianoche (se sustituye en los tests). */
open class CurrentDayProvider @Inject constructor() {
    open fun today(zone: ZoneId): Flow<LocalDate> = flow {
        while (true) {
            val now = ZonedDateTime.now(zone)
            emit(now.toLocalDate())
            // Un segundo de margen para no emitir antes de la medianoche.
            delay(millisUntilNextDay(now) + 1_000)
        }
    }.distinctUntilChanged()
}

/** Milisegundos que faltan para la próxima medianoche en la zona de [now]. */
internal fun millisUntilNextDay(now: ZonedDateTime): Long {
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    return Duration.between(now, nextMidnight).toMillis()
}
