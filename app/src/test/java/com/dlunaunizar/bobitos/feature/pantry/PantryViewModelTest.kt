package com.dlunaunizar.bobitos.feature.pantry

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.PantryItem
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.data.repository.PantryFailure
import com.dlunaunizar.bobitos.data.repository.PantryRepositoryException
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.feature.common.FakePantryRepository
import com.dlunaunizar.bobitos.feature.common.RecordingShoppingRepository
import com.dlunaunizar.bobitos.feature.common.ingredientShoppingSource
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientBrandRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientPrefsRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PantryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pantry = FakePantryRepository()
    private val shopping = RecordingShoppingRepository()
    private val ingredients = FakeIngredientRepository()
    private val viewModel = PantryViewModel(
        pantry,
        shopping,
        ingredientShoppingSource(ingredients, FakeIngredientBrandRepository(), FakeIngredientPrefsRepository()),
    )

    private val salt = PantryItem("sal", "Sal", "Especias y hierbas", null)
    private val tomato = PantryItem("tomat", "Tomate", "Verduras y hortalizas", null)

    private fun shoppingItem(id: String, name: String, purchased: Boolean = false) = ShoppingItem(
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

    @Test
    fun `observes the pantry of the space`() = runTest(mainDispatcherRule.testDispatcher) {
        pantry.itemsState.value = listOf(salt)

        viewModel.observe("home")
        advanceUntilIdle()

        assertEquals("home", pantry.observedSpaceId)
        assertEquals(listOf(salt), (viewModel.uiState.value.items as UiState.Content).value)
    }

    @Test
    fun `a read failure shows the error without the exception text`() = runTest(mainDispatcherRule.testDispatcher) {
        pantry.itemsFailure =
            PantryRepositoryException(PantryFailure.PermissionDenied, IllegalStateException("PERMISSION_DENIED"))

        viewModel.observe("home")
        advanceUntilIdle()

        assertEquals(UiState.Error(), viewModel.uiState.value.items)
    }

    @Test
    fun `finishing removes the item and offers it for the shopping list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            pantry.itemsState.value = listOf(salt, tomato)
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.markFinished(tomato)
            advanceUntilIdle()

            assertEquals(listOf(listOf("tomat")), pantry.removed)
            assertEquals(tomato, viewModel.uiState.value.finished)
            viewModel.consumeFinished()
            assertNull(viewModel.uiState.value.finished)
        }

    @Test
    fun `a failed removal shows the error and offers nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        pantry.itemsState.value = listOf(tomato)
        pantry.removeFailure = PantryRepositoryException(PantryFailure.Network)
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.markFinished(tomato)
        advanceUntilIdle()

        assertEquals(PantryUiMessage.NetworkError, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.finished)
    }

    @Test
    fun `restock adds something new to the shopping list`() = runTest(mainDispatcherRule.testDispatcher) {
        ingredients.catalogState.value = listOf(CatalogIngredient("tomate", "Tomate", "Verduras y hortalizas"))
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.restock(tomato)
        advanceUntilIdle()

        assertEquals(listOf("Tomate"), shopping.addedNames)
        assertEquals(PantryUiMessage.AddedToShopping, viewModel.uiState.value.notice)
    }

    @Test
    fun `restock of something already pending writes nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        shopping.itemsState.value = listOf(shoppingItem("s1", "tomates"))
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.restock(tomato)
        advanceUntilIdle()

        assertTrue(shopping.appliedBatches.isEmpty())
        assertEquals(PantryUiMessage.AlreadyInShopping, viewModel.uiState.value.notice)
    }

    @Test
    fun `restock of a purchased item relists it`() = runTest(mainDispatcherRule.testDispatcher) {
        shopping.itemsState.value = listOf(shoppingItem("s1", "Tomate", purchased = true))
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.restock(tomato)
        advanceUntilIdle()

        assertEquals(listOf("s1"), shopping.relistedIds)
        assertEquals(PantryUiMessage.AddedToShopping, viewModel.uiState.value.notice)
    }

    @Test
    fun `restock reads the server list, not the cache`() = runTest(mainDispatcherRule.testDispatcher) {
        shopping.itemsState.value = emptyList()
        shopping.serverItems = listOf(shoppingItem("s1", "Tomate"))
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.restock(tomato)
        advanceUntilIdle()

        assertTrue(shopping.appliedBatches.isEmpty())
        assertEquals(PantryUiMessage.AlreadyInShopping, viewModel.uiState.value.notice)
    }

    @Test
    fun `a shopping list changed meanwhile asks to try again`() = runTest(mainDispatcherRule.testDispatcher) {
        shopping.itemsState.value = listOf(shoppingItem("s1", "Tomate", purchased = true))
        shopping.applyFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.restock(tomato)
        advanceUntilIdle()

        assertEquals(PantryUiMessage.ShoppingChanged, viewModel.uiState.value.error)
    }

    @Test
    fun `restock without brands in time adds it without brand or store`() = runTest(mainDispatcherRule.testDispatcher) {
        ingredients.catalogOverride = flow { awaitCancellation() }
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.restock(tomato)
        advanceTimeBy(5_001)
        advanceUntilIdle()

        assertEquals(listOf("Tomate"), shopping.addedNames)
        assertEquals(listOf(null), shopping.addedSupermarkets)
        assertEquals(listOf(null), shopping.addedBrands)
    }
}
