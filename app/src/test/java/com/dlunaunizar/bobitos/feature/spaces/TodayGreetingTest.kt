package com.dlunaunizar.bobitos.feature.spaces

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
