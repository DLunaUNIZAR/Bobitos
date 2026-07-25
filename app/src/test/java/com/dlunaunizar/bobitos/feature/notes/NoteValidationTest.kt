package com.dlunaunizar.bobitos.feature.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteValidationTest {
    @Test
    fun `title is required, body is optional`() {
        assertEquals(NoteUiMessage.TitleRequired, NoteValidation.validate(" ", "clave del wifi"))
        assertNull(NoteValidation.validate("Wifi", null))
    }

    @Test
    fun `field limits are validated`() {
        assertEquals(NoteUiMessage.TitleTooLong, NoteValidation.validate("a".repeat(121), null))
        assertEquals(NoteUiMessage.BodyTooLong, NoteValidation.validate("Nota", "a".repeat(5001)))
    }
}
