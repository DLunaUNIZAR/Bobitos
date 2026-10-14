package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.ShoppingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ShoppingFirestoreTest {
    private val seen = ShoppingItem(
        id = "s1",
        name = "Leche",
        quantity = "1 l",
        notes = null,
        purchased = true,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
        purchasedBy = "u",
        purchasedByName = "U",
        purchasedAt = Instant.EPOCH,
        supermarket = null,
        brand = null,
    )

    @Test
    fun `an item still as it was seen is not stale`() {
        assertNull(staleItemFailure(seen.copy(updatedBy = "otro", updatedAt = Instant.now()), seen))
    }

    @Test
    fun `a changed item fails as changed and says which one, as it was seen`() {
        val failure = staleItemFailure(seen.copy(name = "Leche entera", quantity = "2 l"), seen)!!

        assertEquals(ShoppingFailure.ItemChanged, failure.failure)
        assertEquals("Leche", failure.itemName)
    }

    @Test
    fun `a deleted item fails as not found and says which one`() {
        val failure = staleItemFailure(null, seen)!!

        assertEquals(ShoppingFailure.ItemNotFound, failure.failure)
        assertEquals("Leche", failure.itemName)
    }
}
