package com.dlunaunizar.bobitos.feature.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalCalendarBackTest {
    @Test
    fun backWithADayOpenedFromTheMonthReturnsToTheMonth() {
        assertEquals(
            BackAction.ToPreviousMode(CalendarDisplayMode.MONTH),
            personalCalendarBackAction(CalendarDisplayMode.MONTH),
        )
    }

    @Test
    fun backWithoutADrillLeavesTheScreen() {
        assertEquals(BackAction.Leave, personalCalendarBackAction(null))
    }
}
