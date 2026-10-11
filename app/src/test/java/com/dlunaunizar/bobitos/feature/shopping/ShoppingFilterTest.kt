package com.dlunaunizar.bobitos.feature.shopping

import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ShoppingFilterTest {
    private fun item(id: String, supermarket: Supermarket?) = ShoppingItem(
        id = id,
        name = id,
        quantity = null,
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
        supermarket = supermarket,
    )

    private val items = listOf(
        item("carne", Supermarket.CARNICERIA),
        item("leche", Supermarket.MERCADONA),
        item("libre", null),
        item("cualquiera", Supermarket.INDIFERENTE),
        item("varios", Supermarket.OTROS),
    )

    @Test
    fun `filtering by a local shop shows only its items`() {
        Supermarket.entries.filter { it.isLocalShop }.forEach { shop ->
            val expected = items.filter { it.supermarket == shop }.map { it.id }
            assertEquals(expected, items.forSupermarket(shop).map { it.id })
        }
        assertEquals(listOf("carne"), items.forSupermarket(Supermarket.CARNICERIA).map { it.id })
    }

    @Test
    fun `filtering by a supermarket still includes unassigned and indifferent items`() {
        assertEquals(
            listOf("leche", "libre", "cualquiera"),
            items.forSupermarket(Supermarket.MERCADONA).map { it.id },
        )
        assertEquals(
            listOf("libre", "cualquiera", "varios"),
            items.forSupermarket(Supermarket.OTROS).map { it.id },
        )
    }

    @Test
    fun `no filter shows everything`() {
        assertEquals(items, items.forSupermarket(null))
    }

    @Test
    fun `filter chips follow the enum order`() {
        val shuffled = listOf(
            item("a", Supermarket.CARNICERIA),
            item("b", Supermarket.OTROS),
            item("c", Supermarket.MERCADONA),
            item("d", Supermarket.CARNICERIA),
        )
        assertEquals(
            listOf(Supermarket.MERCADONA, Supermarket.CARNICERIA, Supermarket.OTROS),
            shuffled.presentSupermarkets(),
        )
    }
}
