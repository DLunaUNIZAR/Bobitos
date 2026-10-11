package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientBrandRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientPrefsRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakePersonalIngredientRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class IngredientShoppingSourceTest {
    private val common = FakeIngredientRepository()
    private val personal = FakePersonalIngredientRepository()
    private val brands = FakeIngredientBrandRepository()
    private val prefs = FakeIngredientPrefsRepository()
    private val source = IngredientShoppingSource(common, personal, brands, prefs)
    private val brand = IngredientBrand(id = "b", ingredientId = "tomate", name = "B")

    @Test
    fun `data merges common and personal and carries brands and prefs`() = runTest {
        common.catalogState.value = listOf(CatalogIngredient("tomate", "Tomate"))
        personal.state.value = listOf(CatalogIngredient("kale", "Kale", personal = true))
        brands.brandsState.value = listOf(brand)
        prefs.state.value = mapOf("tomate" to IngredientPref(listOf(Supermarket.DIA)))

        val data = source.data().first()

        assertEquals(setOf("tomate", "kale"), data.ingredients.map { it.id }.toSet())
        assertEquals(listOf(brand), data.brands)
        assertEquals(mapOf("tomate" to IngredientPref(listOf(Supermarket.DIA))), data.prefs)
    }

    @Test
    fun `a failing source still delivers the others`() = runTest {
        common.failCatalog = true
        personal.state.value = listOf(CatalogIngredient("kale", "Kale", personal = true))
        brands.brandsState.value = listOf(brand)
        prefs.state.value = mapOf("tomate" to IngredientPref(listOf(Supermarket.DIA)))

        val data = source.data().first()

        assertEquals(listOf("kale"), data.ingredients.map { it.id })
        assertEquals(listOf(brand), data.brands)
        assertEquals(1, data.prefs.size)
    }
}
