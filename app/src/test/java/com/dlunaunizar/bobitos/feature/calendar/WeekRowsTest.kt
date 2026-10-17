package com.dlunaunizar.bobitos.feature.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekRowsTest {
    private fun d(day: Int) = LocalDate.of(2026, 10, day)

    private val week = (12..18).map(::d)

    private fun day(n: Int) = WeekRow.Day(d(n))

    private fun run(a: Int, b: Int) = WeekRow.EmptyRun(d(a), d(b))

    @Test
    fun emptyDaysInTheMiddleAreGrouped() {
        val rows = weekRows(week, setOf(d(12), d(17)), today = d(17))
        assertEquals(listOf(day(12), run(13, 16), day(17), run(18, 18)), rows)
    }

    @Test
    fun aWholeEmptyWeekIsOneRunWhenTodayIsOutside() {
        assertEquals(listOf(run(12, 18)), weekRows(week, emptySet(), today = d(20)))
    }

    @Test
    fun todayNeverFolds() {
        val rows = weekRows(week, setOf(d(12)), today = d(15))
        assertEquals(listOf(day(12), run(13, 14), day(15), run(16, 18)), rows)
    }

    @Test
    fun aFullWeekHasNoRuns() {
        val rows = weekRows(week, week.toSet(), today = d(17))
        assertEquals(week.map { WeekRow.Day(it) }, rows)
    }

    @Test
    fun emptyDaysAtTheStart() {
        val rows = weekRows(week, setOf(d(18)), today = d(17))
        assertEquals(listOf(run(12, 16), day(17), day(18)), rows)
    }
}
