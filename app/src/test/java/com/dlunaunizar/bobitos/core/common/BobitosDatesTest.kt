package com.dlunaunizar.bobitos.core.common

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale

class BobitosDatesTest {
    private lateinit var previous: Locale

    @Before
    fun setUp() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(previous)
    }

    @Test
    fun `month is in Spanish with a capital`() {
        assertEquals("Octubre de 2026", BobitosDates.month(YearMonth.of(2026, 10)))
    }

    @Test
    fun `longDay is in Spanish with a capital`() {
        assertEquals("Sábado, 17 de octubre", BobitosDates.longDay(LocalDate.of(2026, 10, 17)))
    }

    @Test
    fun `weekRange inside a month`() {
        assertEquals(
            "12–18 oct 2026",
            BobitosDates.weekRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)),
        )
    }

    @Test
    fun `weekRange crossing months`() {
        assertEquals(
            "28 sept – 4 oct 2026",
            BobitosDates.weekRange(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4)),
        )
    }

    @Test
    fun `dayHeader is weekday and number`() {
        assertEquals("Lunes 12", BobitosDates.dayHeader(LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun `shortDate and dateTime and time`() {
        assertEquals("17 oct 2026", BobitosDates.shortDate(LocalDate.of(2026, 10, 17)))
        assertEquals("17 oct, 10:30", BobitosDates.dateTime(LocalDateTime.of(2026, 10, 17, 10, 30)))
        assertEquals("10:30", BobitosDates.time(LocalTime.of(10, 30)))
    }

    @Test
    fun `accessibleDate is lowercase and complete`() {
        assertEquals("sábado, 17 de octubre de 2026", BobitosDates.accessibleDate(LocalDate.of(2026, 10, 17)))
    }

    @Test
    fun `day initials use X for Wednesday`() {
        assertEquals(listOf("L", "M", "X", "J", "V", "S", "D"), DayOfWeek.entries.map(BobitosDates::dayInitial))
    }

    @Test
    fun `shortDay abbreviates the weekday in Spanish`() {
        assertEquals("mar 13", BobitosDates.shortDay(LocalDate.of(2026, 10, 13)))
        assertEquals("mié 14", BobitosDates.shortDay(LocalDate.of(2026, 10, 14)))
    }

    @Test
    fun `shortDayRange joins both ends and collapses a single day`() {
        assertEquals(
            "mar 13 – vie 16",
            BobitosDates.shortDayRange(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 16)),
        )
        assertEquals(
            "dom 18",
            BobitosDates.shortDayRange(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 18)),
        )
    }

    @Test
    fun `rangoAccesibleEnElMismoMes`() {
        assertEquals(
            "del martes 13 al viernes 16 de octubre",
            BobitosDates.accessibleRange(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 16)),
        )
    }

    @Test
    fun `rangoAccesibleEntreMeses`() {
        assertEquals(
            "del jueves 29 de octubre al lunes 2 de noviembre",
            BobitosDates.accessibleRange(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 2)),
        )
    }

    @Test
    fun `accessibleDay is lowercase and has no year`() {
        assertEquals("domingo, 18 de octubre", BobitosDates.accessibleDay(LocalDate.of(2026, 10, 18)))
    }

    @Test
    fun dayMonthSinAnioSiEsEsteAnio() {
        assertEquals("12 de octubre", BobitosDates.dayMonth(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)))
    }

    @Test
    fun dayMonthConAnioSiEsDeOtroAnio() {
        assertEquals(
            "30 de diciembre de 2025",
            BobitosDates.dayMonth(LocalDate.of(2025, 12, 30), LocalDate.of(2026, 1, 1)),
        )
    }
}
