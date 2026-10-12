package com.dlunaunizar.bobitos.feature.shopping

import com.dlunaunizar.bobitos.core.model.ShoppingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ShoppingDuplicateTest {
    private fun item(quantity: String?) = ShoppingItem(
        id = "milk", name = "Leche", quantity = quantity, notes = null, purchased = false,
        createdBy = "u", createdByName = "U", createdAt = Instant.EPOCH, updatedBy = "u", updatedAt = Instant.EPOCH,
        purchasedBy = null, purchasedByName = null, purchasedAt = null,
    )

    @Test
    fun `the sum is ready when the notice opens and says when it does not fit`() {
        assertEquals("3 l", ShoppingDuplicate(item("1 l"), "2 l").summed)
        assertFalse(ShoppingDuplicate(item("1 l"), "2 l").sumTooLong)
        assertTrue(ShoppingDuplicate(item("x".repeat(30)), "y".repeat(10)).sumTooLong)
    }

    @Test
    fun `the existing quantity does not break across lines and is absent when there is none`() {
        assertEquals("5 / ud", ShoppingDuplicate(item(" 5 / ud "), "2").existingQuantityLabel)
        assertNull(ShoppingDuplicate(item(null), "2").existingQuantityLabel)
        assertNull(ShoppingDuplicate(item("  "), "2").existingQuantityLabel)
    }
}
