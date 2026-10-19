package com.dlunaunizar.bobitos.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoGuardsTest {
    @Test
    fun `only the last writer can undo an add`() {
        assertTrue(isLastWriter(updatedBy = "me", userId = "me"))
        assertFalse(isLastWriter(updatedBy = "ana", userId = "me"))
        assertFalse(isLastWriter(updatedBy = null, userId = "me"))
    }
}
