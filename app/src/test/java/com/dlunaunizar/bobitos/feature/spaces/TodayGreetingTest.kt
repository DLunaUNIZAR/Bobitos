package com.dlunaunizar.bobitos.feature.spaces

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TodayGreetingTest {
    @Test
    fun elSaludoDependeDeLaHora() {
        assertEquals(DayPeriod.MORNING, dayPeriodFor(5))
        assertEquals(DayPeriod.MORNING, dayPeriodFor(11))
        assertEquals(DayPeriod.AFTERNOON, dayPeriodFor(12))
        assertEquals(DayPeriod.AFTERNOON, dayPeriodFor(19))
        assertEquals(DayPeriod.EVENING, dayPeriodFor(20))
        assertEquals(DayPeriod.EVENING, dayPeriodFor(3))
    }

    @Test
    fun elNombreEsLaPrimeraPalabra() {
        assertEquals("David", firstName("David Luna"))
        assertEquals("Ana", firstName("  Ana  "))
        assertEquals("", firstName("   "))
    }

    @Test
    fun faltaParaElSiguienteCambioDePeriodo() {
        fun at(time: LocalTime) = ZonedDateTime.of(LocalDate.of(2026, 10, 17), time, ZoneId.of("UTC"))

        assertEquals(30_000L, millisUntilNextDayPeriod(at(LocalTime.of(11, 59, 30))))
        // Justo en el cambio, el siguiente es el de después.
        assertEquals(8 * 3_600_000L, millisUntilNextDayPeriod(at(LocalTime.of(12, 0))))
        assertEquals(9 * 3_600_000L, millisUntilNextDayPeriod(at(LocalTime.of(20, 0))))
        // De noche, el siguiente es el de las 5 del día siguiente (o del mismo día si aún es de madrugada).
        assertEquals(8 * 3_600_000L, millisUntilNextDayPeriod(at(LocalTime.of(21, 0))))
        assertEquals(2 * 3_600_000L, millisUntilNextDayPeriod(at(LocalTime.of(3, 0))))
    }
}
