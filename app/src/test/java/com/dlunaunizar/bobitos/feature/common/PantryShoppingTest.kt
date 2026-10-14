package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.core.model.Supermarket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
