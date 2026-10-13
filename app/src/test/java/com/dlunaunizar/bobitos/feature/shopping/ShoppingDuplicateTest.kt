package com.dlunaunizar.bobitos.feature.shopping

import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
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
    fun `only the space after the leading number does not break, so a long quantity breaks between words`() {
        assertEquals("5 ud", ShoppingDuplicate(item(" 5 ud "), "2").existingQuantityLabel)
        assertEquals(
            "2 paquetes grandes del supermercado",
            ShoppingDuplicate(item("2 paquetes grandes del supermercado"), "1").existingQuantityLabel,
        )
        assertEquals(
            "1/2 cucharadas soperas",
            ShoppingDuplicate(item("1/2 cucharadas soperas"), "1").existingQuantityLabel,
        )
        assertEquals("un poco", ShoppingDuplicate(item("un poco"), "1").existingQuantityLabel)
        assertEquals("x".repeat(30), ShoppingDuplicate(item("x".repeat(30)), "1").existingQuantityLabel)
        assertNull(ShoppingDuplicate(item(null), "2").existingQuantityLabel)
        assertNull(ShoppingDuplicate(item("  "), "2").existingQuantityLabel)
    }

    @Test
    fun `summing fills the store and the brand only as a pair that goes together`() {
        fun after(store: Supermarket?, brand: String?, typedStore: Supermarket?, typedBrand: String?) =
            ShoppingDuplicate(item("1 l").copy(supermarket = store, brand = brand), "1 l", null, typedStore, typedBrand)
                .storeAndBrandAfterSum()

        assertEquals(Supermarket.MERCADONA to "Hacendado", after(null, null, Supermarket.MERCADONA, "Hacendado"))
        assertEquals(Supermarket.DIA to null, after(Supermarket.DIA, null, Supermarket.MERCADONA, "Hacendado"))
        assertEquals(Supermarket.DIA to "Dia", after(Supermarket.DIA, "  ", Supermarket.DIA, "Dia"))
        assertEquals(
            Supermarket.MERCADONA to "Hacendado",
            after(null, "Hacendado", Supermarket.MERCADONA, " hacendado "),
        )
        assertEquals(null to "Pascual", after(null, "Pascual", Supermarket.DIA, "Puleva"))
        assertEquals(
            Supermarket.DIA to "Pascual",
            after(Supermarket.DIA, "Pascual", Supermarket.MERCADONA, "Hacendado"),
        )
        assertEquals(Supermarket.INDIFERENTE to null, after(Supermarket.INDIFERENTE, null, Supermarket.DIA, "Puleva"))
    }
}
