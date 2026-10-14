package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.PantryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
