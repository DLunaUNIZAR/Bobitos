package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanLogicTest {
    @Test
    fun `finds a brand by barcode ignoring spaces and non digits`() {
        val brands = listOf(
            IngredientBrand("b0", "sal", "Sin código"),
            IngredientBrand("b1", "tomate", "Hacendado", barcode = "8410 000-000000"),
        )

        assertEquals("b1", findBrandByBarcode(brands, "8410000000000")?.id)
        assertEquals("b1", findBrandByBarcode(brands, " 8410000000000\n")?.id)
        assertNull(findBrandByBarcode(brands, "1234567890123"))
        assertNull(findBrandByBarcode(brands, "  "))
    }

    @Test
    fun `suggests ingredients whose key words are all in the product name, most specific first`() {
        val ingredients = listOf(
            CatalogIngredient("cacao", "Cacao"),
            CatalogIngredient("leche", "Leche"),
            CatalogIngredient("chocolate-negro", "Chocolate negro"),
            CatalogIngredient("chocolate-con-leche", "Chocolate con leche"),
        )

        val suggestions = suggestIngredients(ingredients, "Chocolate negro 85 % cacao Lindt")

        assertEquals(listOf("Chocolate negro", "Cacao"), suggestions.map(CatalogIngredient::name))
    }

    @Test
    fun `suggestions are capped and empty for a blank product`() {
        val ingredients = (1..12).map { CatalogIngredient("i$it", "Cacao") } +
            CatalogIngredient("vacio", "de la")

        assertEquals(3, suggestIngredients(ingredients, "Cacao puro", max = 3).size)
        assertEquals(emptyList<CatalogIngredient>(), suggestIngredients(ingredients, "   "))
        assertEquals(emptyList<CatalogIngredient>(), suggestIngredients(ingredients, ""))
    }
}
