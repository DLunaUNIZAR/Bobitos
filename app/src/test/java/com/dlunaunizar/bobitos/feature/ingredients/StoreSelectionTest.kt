package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.model.Supermarket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StoreSelectionTest {
    @Test
    fun `toggle appends at the end and removes when present`() {
        val one = toggleStore(emptyList(), Supermarket.DIA)
        val two = toggleStore(one, Supermarket.MERCADONA)
        assertEquals(listOf(Supermarket.DIA, Supermarket.MERCADONA), two)

        assertEquals(listOf(Supermarket.MERCADONA), toggleStore(two, Supermarket.DIA))
        val removed = toggleStore(two, Supermarket.DIA)
        assertEquals(listOf(Supermarket.MERCADONA, Supermarket.DIA), toggleStore(removed, Supermarket.DIA))
    }

    @Test
    fun `INDIFERENTE is never selectable or added`() {
        assertFalse(Supermarket.INDIFERENTE in SELECTABLE_STORES)
        assertEquals(Supermarket.entries.size - 1, SELECTABLE_STORES.size)
        assertEquals(listOf(Supermarket.DIA), toggleStore(listOf(Supermarket.DIA), Supermarket.INDIFERENTE))
        assertEquals(emptyList<Supermarket>(), toggleStore(emptyList(), Supermarket.INDIFERENTE))
    }
}
