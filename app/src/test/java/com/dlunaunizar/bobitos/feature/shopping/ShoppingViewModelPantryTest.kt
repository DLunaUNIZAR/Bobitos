package com.dlunaunizar.bobitos.feature.shopping

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.feature.common.FakePantryRepository
import com.dlunaunizar.bobitos.feature.common.ingredientShoppingSource
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientBrandRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientPrefsRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakePersonalIngredientRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

// Marcar comprado: el catálogo con el que se reconoce el ingrediente para la despensa.
@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelPantryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeShoppingRepository()
    private val ingredientRepository = FakeIngredientRepository()
    private val personalRepository = FakePersonalIngredientRepository()
    private val viewModel by lazy {
        ShoppingViewModel(
            repository,
            ingredientRepository,
            personalRepository,
            ingredientShoppingSource(
                ingredientRepository,
                FakeIngredientBrandRepository(),
                FakeIngredientPrefsRepository(),
                personalRepository,
            ),
            FakePantryRepository(),
        )
    }

    @Test
    fun `marking a recognized ingredient sends its pantry entry`() = runTest(mainDispatcherRule.testDispatcher) {
        ingredientRepository.catalogState.value = listOf(CatalogIngredient("tomate", "Tomate", "Verduras y hortalizas"))
        repository.itemState.value = listOf(shoppingItem().copy(id = "t1", name = "tomates"))
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.setPurchased("home", "t1", true)
        advanceUntilIdle()

        assertEquals(Triple("home", "t1", true), repository.purchaseChange)
        assertEquals(PantryEntry("Tomate", "Verduras y hortalizas"), repository.purchaseEntry)
    }

    @Test
    fun `marking something that is not an ingredient sends no pantry entry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogState.value =
                listOf(CatalogIngredient("tomate", "Tomate", "Verduras y hortalizas"))
            repository.itemState.value = listOf(shoppingItem().copy(id = "w1", name = "Lavavajillas"))
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.setPurchased("home", "w1", true)
            advanceUntilIdle()

            assertEquals(Triple("home", "w1", true), repository.purchaseChange)
            assertNull(repository.purchaseEntry)
        }

    @Test
    fun `unmarking sends no pantry entry`() = runTest(mainDispatcherRule.testDispatcher) {
        ingredientRepository.catalogState.value = listOf(CatalogIngredient("tomate", "Tomate", "Verduras y hortalizas"))
        repository.itemState.value = listOf(shoppingItem().copy(id = "t1", name = "tomates", purchased = true))
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.setPurchased("home", "t1", false)
        advanceUntilIdle()

        assertEquals(Triple("home", "t1", false), repository.purchaseChange)
        assertNull(repository.purchaseEntry)
    }

    @Test
    fun `marking without the catalog waits at most 5 seconds and marks without an entry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogOverride = flow { awaitCancellation() }
            repository.itemState.value = listOf(shoppingItem().copy(id = "t1", name = "tomates"))
            viewModel.observe("home")
            runCurrent()

            viewModel.setPurchased("home", "t1", true)
            advanceTimeBy(4_999)
            runCurrent()
            assertNull(repository.purchaseChange)

            advanceTimeBy(2)
            runCurrent()
            assertEquals(Triple("home", "t1", true), repository.purchaseChange)
            assertNull(repository.purchaseEntry)
            assertEquals(ShoppingUiMessage.ItemMarked, viewModel.uiState.value.save.notice)
        }

    @Test
    fun `a slow catalog serves the later marks without waiting again`() = runTest(mainDispatcherRule.testDispatcher) {
        ingredientRepository.catalogOverride = flow {
            delay(8_000)
            emit(listOf(CatalogIngredient("tomate", "Tomate", "Verduras y hortalizas")))
        }
        repository.itemState.value = listOf(
            shoppingItem().copy(id = "t1", name = "tomates"),
            shoppingItem().copy(id = "t2", name = "tomate"),
        )
        viewModel.observe("home")
        runCurrent()

        viewModel.setPurchased("home", "t1", true)
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(Triple("home", "t1", true), repository.purchaseChange)
        assertNull(repository.purchaseEntry)

        advanceTimeBy(3_000)
        runCurrent()
        viewModel.setPurchased("home", "t2", true)
        runCurrent()

        assertEquals(Triple("home", "t2", true), repository.purchaseChange)
        assertEquals(PantryEntry("Tomate", "Verduras y hortalizas"), repository.purchaseEntry)
    }

    @Test
    fun `an ingredient created from an item is recognized when it is marked`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogState.value =
                listOf(CatalogIngredient("tomate", "Tomate", "Verduras y hortalizas"))
            repository.itemState.value = listOf(
                shoppingItem().copy(id = "t1", name = "tomates"),
                shoppingItem().copy(id = "m1", name = "Mango"),
            )
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.setPurchased("home", "t1", true)
            advanceUntilIdle()

            viewModel.createIngredientFromItem(shoppingItem().copy(id = "m1", name = "Mango"))
            advanceUntilIdle()
            viewModel.setPurchased("home", "m1", true)
            advanceUntilIdle()

            assertEquals(Triple("home", "m1", true), repository.purchaseChange)
            assertEquals("Mango", repository.purchaseEntry?.name)
        }
}
