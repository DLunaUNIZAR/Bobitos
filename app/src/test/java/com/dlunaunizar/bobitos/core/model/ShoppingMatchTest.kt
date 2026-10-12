package com.dlunaunizar.bobitos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ShoppingMatchTest {
    @Test
    fun `the key ignores plurals, accents, case and empty words`() {
        assertEquals(shoppingMatchKey("Tomate"), shoppingMatchKey("tomates"))
        assertEquals(shoppingMatchKey("Limón"), shoppingMatchKey("LIMONES"))
        assertEquals(shoppingMatchKey("Pan de molde"), shoppingMatchKey("pan molde"))
    }

    @Test
    fun `different products keep different keys`() {
        assertNotEquals(shoppingMatchKey("Leche entera"), shoppingMatchKey("Leche"))
        assertNotEquals(shoppingMatchKey("Tomate frito"), shoppingMatchKey("Tomate"))
    }

    @Test
    fun `a name without catalog key falls back to the trimmed lowercase name`() {
        assertEquals("de", shoppingMatchKey(" De "))
        assertEquals("¡¡!!", shoppingMatchKey("¡¡!!"))
    }

    @Test
    fun `the match prefers a pending item over a purchased one`() {
        val items = listOf(item("a", "Tomates", purchased = true), item("b", "tomate"), item("c", "Tomate"))

        assertEquals("b", findShoppingMatch(items, "Tomate")?.id)
    }

    @Test
    fun `with only purchased matches the first purchased one is returned`() {
        val items = listOf(item("a", "Leche", purchased = true), item("b", "leche", purchased = true))

        assertEquals("a", findShoppingMatch(items, "LECHE")?.id)
    }

    @Test
    fun `blank names and other products never match`() {
        val items = listOf(item("a", "Leche"), item("b", "¡¡!!"))

        assertNull(findShoppingMatch(items, "   "))
        assertNull(findShoppingMatch(items, "Pan"))
        assertEquals("b", findShoppingMatch(items, " ¡¡!! ")?.id)
    }

    private fun item(id: String, name: String, purchased: Boolean = false) = ShoppingItem(
        id = id,
        name = name,
        quantity = null,
        notes = null,
        purchased = purchased,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
        purchasedBy = null,
        purchasedByName = null,
        purchasedAt = null,
    )
}
