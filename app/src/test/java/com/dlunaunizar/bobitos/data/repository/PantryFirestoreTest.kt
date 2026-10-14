package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.PantryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PantryFirestoreTest {
    private fun failureOf(entry: PantryEntry): PantryFailure? = try {
        validatePantryEntry(entry)
        null
    } catch (error: PantryRepositoryException) {
        error.failure
    }

    @Test
    fun `validatePantryEntry trims the name and the category`() {
        assertEquals(
            PantryEntry("Sal", "Especias y hierbas"),
            validatePantryEntry(PantryEntry("  Sal ", " Especias y hierbas ")),
        )
    }

    @Test
    fun `a blank category becomes null and a too long one is dropped`() {
        assertNull(validatePantryEntry(PantryEntry("Sal", "  ")).category)
        assertNull(validatePantryEntry(PantryEntry("Sal", "a".repeat(61))).category)
        assertEquals("a".repeat(60), validatePantryEntry(PantryEntry("Sal", "a".repeat(60))).category)
    }

    @Test
    fun `a name without letters or numbers is required`() {
        assertEquals(PantryFailure.NameRequired, failureOf(PantryEntry("   ", null)))
        assertEquals(PantryFailure.NameRequired, failureOf(PantryEntry("¡¿!?", null)))
    }

    @Test
    fun `the name allows 120 characters after trimming`() {
        assertEquals("a".repeat(120), validatePantryEntry(PantryEntry(" " + "a".repeat(120) + " ", null)).name)
        assertEquals(PantryFailure.NameTooLong, failureOf(PantryEntry("a".repeat(121), null)))
    }

    private val bought = Instant.parse("2026-10-13T10:00:00.123456Z")

    @Test
    fun `an entry created by this purchase of the same item is removed`() {
        assertTrue(shouldRemovePantryEntry("x", bought, "x", bought))
    }

    @Test
    fun `an entry of the same item from an earlier purchase is kept`() {
        assertFalse(shouldRemovePantryEntry("x", bought.minusSeconds(3600), "x", bought))
    }

    @Test
    fun `an entry of another item or of none is kept`() {
        assertFalse(shouldRemovePantryEntry("y", bought, "x", bought))
        assertFalse(shouldRemovePantryEntry(null, bought, "x", bought))
    }

    @Test
    fun `missing times keep the entry`() {
        assertFalse(shouldRemovePantryEntry("x", null, "x", bought))
        assertFalse(shouldRemovePantryEntry("x", bought, "x", null))
        assertFalse(shouldRemovePantryEntry("x", null, "x", null))
    }
}
