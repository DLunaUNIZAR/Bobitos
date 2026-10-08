package com.dlunaunizar.bobitos.feature.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarValidationTest {
    @Test
    fun `a blank title is required`() {
        assertEquals(CalendarValidation.Error.TitleRequired, CalendarValidation.validate("  ", ""))
    }

    @Test
    fun `a title over 120 characters is too long`() {
        assertEquals(CalendarValidation.Error.TitleTooLong, CalendarValidation.validate("a".repeat(121), ""))
    }

    @Test
    fun `a description over 1000 characters is too long`() {
        assertEquals(
            CalendarValidation.Error.DescriptionTooLong,
            CalendarValidation.validate("Cena", "a".repeat(1001)),
        )
    }

    @Test
    fun `limits are checked on trimmed text`() {
        assertNull(CalendarValidation.validate(" ${"a".repeat(120)} ", " ${"b".repeat(1000)} "))
    }
}
