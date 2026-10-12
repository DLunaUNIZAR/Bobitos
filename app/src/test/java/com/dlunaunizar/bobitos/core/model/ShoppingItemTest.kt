package com.dlunaunizar.bobitos.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ShoppingItemTest {
    private val item = ShoppingItem(
        id = "s1",
        name = "Leche",
        quantity = "1 l",
        notes = null,
        purchased = false,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
        purchasedBy = null,
        purchasedByName = null,
        purchasedAt = null,
        supermarket = Supermarket.DIA,
        brand = "Pascual",
    )

    @Test
    fun `the same content ignores who and when but not what the list shows`() {
        assertTrue(item.sameContentAs(item.copy(updatedBy = "otro", updatedAt = Instant.now(), createdByName = "Otro")))
        assertFalse(item.sameContentAs(item.copy(name = "Leche sin lactosa")))
        assertFalse(item.sameContentAs(item.copy(quantity = "2 l")))
        assertFalse(item.sameContentAs(item.copy(notes = "entera")))
        assertFalse(item.sameContentAs(item.copy(purchased = true)))
        assertFalse(item.sameContentAs(item.copy(supermarket = Supermarket.EROSKI)))
        assertFalse(item.sameContentAs(item.copy(brand = "Puleva")))
    }
}
