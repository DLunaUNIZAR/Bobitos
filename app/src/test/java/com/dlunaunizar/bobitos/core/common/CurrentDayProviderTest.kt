package com.dlunaunizar.bobitos.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CurrentDayProviderTest {
    @Test
    fun faltaParaMedianoche() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 10, 17), LocalTime.of(23, 59, 30), ZoneId.of("UTC"))

        assertEquals(30_000L, millisUntilNextDay(now))
    }
}
