package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.data.repository.ShoppingWrite
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PantryShoppingTest {
    private val tomato = CatalogIngredient("tomate", "Tomate", "Verduras y hortalizas")
    private val data = IngredientShoppingData(
        ingredients = listOf(tomato),
        brands = listOf(
            IngredientBrand("b1", "tomate", "Orlando", favorite = true, stores = listOf(Supermarket.MERCADONA)),
        ),
    )

    @Test
    fun `pantryEntryFor recognizes a plural with the canonical name and category`() {
        assertEquals(PantryEntry("Tomate", "Verduras y hortalizas"), pantryEntryFor("tomates", data))
    }

    @Test
    fun `pantryEntryFor recognizes a personal ingredient`() {
        val personal = CatalogIngredient("salsa-de-la-abuela", "Salsa de la abuela", "Caprichos", personal = true)

        assertEquals(
            PantryEntry("Salsa de la abuela", "Caprichos"),
            pantryEntryFor("salsa de la abuela", IngredientShoppingData(listOf(personal))),
        )
    }

    @Test
    fun `pantryEntryFor is null for something that is not an ingredient`() {
        assertNull(pantryEntryFor("Lavavajillas", data))
        assertNull(pantryEntryFor("¡¿!?", data))
    }

    @Test
    fun `restock from the pantry reads the server list and writes once`() = runTest {
        val shopping = RecordingShoppingRepository()
        shopping.itemsState.value = listOf(shoppingItem("s1", "Tomate"))
        shopping.serverItems = listOf(shoppingItem("s1", "Tomate", purchased = true))

        val plan = restockFromPantry("home", listOf("tomates"), shopping, data)

        assertEquals(listOf("s1"), shopping.relistedIds)
        assertEquals(1, shopping.appliedBatches.size)
        assertTrue(plan.alreadyListed.isEmpty())
    }

    @Test
    fun `restock from the pantry of something already pending writes nothing`() = runTest {
        val shopping = RecordingShoppingRepository()
        shopping.serverItems = listOf(shoppingItem("s1", "Tomate"))

        val plan = restockFromPantry("home", listOf("tomates"), shopping, data)

        assertTrue(shopping.appliedBatches.isEmpty())
        assertEquals(listOf("Tomate"), plan.alreadyListed)
    }

    @Test
    fun `restock from the pantry with an item deleted meanwhile reports a changed list`() = runTest {
        val shopping = RecordingShoppingRepository()
        shopping.serverItems = listOf(shoppingItem("s1", "Tomate", purchased = true))
        shopping.applyFailure = ShoppingRepositoryException(ShoppingFailure.ItemNotFound)

        val failure = runCatching { restockFromPantry("home", listOf("Tomate"), shopping, data) }.exceptionOrNull()

        assertEquals(ShoppingFailure.ItemChanged, (failure as ShoppingRepositoryException).failure)
    }

    private fun shoppingItem(
        id: String,
        name: String,
        purchased: Boolean = false,
        quantity: String? = null,
        notes: String? = null,
        supermarket: Supermarket? = null,
        brand: String? = null,
    ) = ShoppingItem(
        id = id,
        name = name,
        quantity = quantity,
        notes = notes,
        purchased = purchased,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
        purchasedBy = null,
        purchasedByName = null,
        purchasedAt = null,
        supermarket = supermarket,
        brand = brand,
    )

    @Test
    fun `restock of something new adds it with the default brand and store and no quantity`() {
        val plan = pantryRestock(listOf("Tomate"), emptyList(), data)

        assertEquals(listOf(ShoppingWrite.Add("Tomate", null, null, Supermarket.MERCADONA, "Orlando")), plan.writes)
        assertTrue(plan.alreadyListed.isEmpty())
    }

    @Test
    fun `restock of something not in the catalog adds it without brand or store`() {
        assertEquals(
            listOf(ShoppingWrite.Add("Papel de cocina", null, null, null, null)),
            pantryRestock(listOf("Papel de cocina"), emptyList(), data).writes,
        )
    }

    @Test
    fun `restock of a pending item writes nothing and reports it`() {
        val plan = pantryRestock(listOf("Tomate"), listOf(shoppingItem("s1", "tomates")), data)

        assertTrue(plan.writes.isEmpty())
        assertEquals(listOf("tomates"), plan.alreadyListed)
    }

    @Test
    fun `restock of a purchased item relists it keeping quantity, notes, store and brand`() {
        val bought = shoppingItem(
            "s1",
            "Tomates",
            purchased = true,
            quantity = "1 kg",
            notes = "maduros",
            supermarket = Supermarket.DIA,
            brand = "Huerta",
        )

        assertEquals(
            listOf(ShoppingWrite.Change(bought, "1 kg", "maduros", Supermarket.DIA, "Huerta", relist = true)),
            pantryRestock(listOf("Tomate"), listOf(bought), data).writes,
        )
    }

    @Test
    fun `restock ignores blank names and repeats by key`() {
        assertEquals(1, pantryRestock(listOf("Tomate", "tomates", "  "), emptyList(), data).writes.size)
    }
}
