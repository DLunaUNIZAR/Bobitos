package com.dlunaunizar.bobitos.feature.pantry

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.PantryEntry
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
    private val viewModel by lazy {
        PantryViewModel(
            pantry,
            shopping,
            ingredientShoppingSource(ingredients, FakeIngredientBrandRepository(), FakeIngredientPrefsRepository()),
        )
    }

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

        assertEquals(PantryUiMessage.NetworkError, viewModel.uiState.value.save.error)
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
        assertEquals(PantryUiMessage.AddedToShopping, viewModel.uiState.value.save.notice)
    }

    @Test
    fun `restock of something already pending writes nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        shopping.itemsState.value = listOf(shoppingItem("s1", "tomates"))
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.restock(tomato)
        advanceUntilIdle()

        assertTrue(shopping.appliedBatches.isEmpty())
        assertEquals(PantryUiMessage.AlreadyInShopping, viewModel.uiState.value.save.notice)
    }

    @Test
    fun `restock of a purchased item relists it`() = runTest(mainDispatcherRule.testDispatcher) {
        shopping.itemsState.value = listOf(shoppingItem("s1", "Tomate", purchased = true))
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.restock(tomato)
        advanceUntilIdle()

        assertEquals(listOf("s1"), shopping.relistedIds)
        assertEquals(PantryUiMessage.AddedToShopping, viewModel.uiState.value.save.notice)
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
        assertEquals(PantryUiMessage.AlreadyInShopping, viewModel.uiState.value.save.notice)
    }

    @Test
    fun `a shopping list changed meanwhile asks to try again`() = runTest(mainDispatcherRule.testDispatcher) {
        shopping.itemsState.value = listOf(shoppingItem("s1", "Tomate", purchased = true))
        shopping.applyFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.restock(tomato)
        advanceUntilIdle()

        assertEquals(PantryUiMessage.ShoppingChanged, viewModel.uiState.value.save.error)
    }

    @Test
    fun `restock without brands in time adds it without brand or store`() = runTest(mainDispatcherRule.testDispatcher) {
        ingredients.catalogOverride = flow { awaitCancellation() }
        viewModel.observe("home")
        runCurrent()

        viewModel.restock(tomato)
        advanceTimeBy(4_999)
        runCurrent()
        assertTrue(shopping.addedNames.isEmpty())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("Tomate"), shopping.addedNames)
        assertEquals(listOf(null), shopping.addedSupermarkets)
        assertEquals(listOf(null), shopping.addedBrands)
    }

    @Test
    fun `restock while another action is saving still adds the item`() = runTest(mainDispatcherRule.testDispatcher) {
        pantry.itemsState.value = listOf(salt, tomato)
        viewModel.observe("home")
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        pantry.removeGate = gate
        viewModel.markFinished(salt)
        runCurrent()
        assertTrue(viewModel.uiState.value.save.isSaving)

        viewModel.restock(tomato)
        advanceUntilIdle()

        assertEquals(listOf("Tomate"), shopping.addedNames)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(listOf("sal")), pantry.removed)
    }

    @Test
    fun `restock uses the catalog the picker already loaded without waiting`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredients.catalogState.value = listOf(CatalogIngredient("tomate", "Tomate", "Verduras y hortalizas"))
            viewModel.observe("home")
            viewModel.openPicker()
            advanceUntilIdle()
            ingredients.catalogOverride = flow { awaitCancellation() }

            viewModel.restock(tomato)
            runCurrent()

            assertEquals(listOf("Tomate"), shopping.addedNames)
        }

    @Test
    fun `the picker loads the catalog while open`() = runTest(mainDispatcherRule.testDispatcher) {
        ingredients.catalogState.value = listOf(CatalogIngredient("sal", "Sal", "Especias y hierbas"))
        viewModel.observe("home")

        viewModel.openPicker()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.picker?.catalogLoaded)
        assertEquals(listOf("Sal"), viewModel.uiState.value.ingredientData.ingredients.map { it.name })
        viewModel.closePicker()
        assertNull(viewModel.uiState.value.picker)
    }

    @Test
    fun `toggling the same item twice unselects it`() {
        viewModel.openPicker()

        viewModel.togglePick(PantryEntry("Sal", "Especias y hierbas"))
        assertEquals(setOf("sal"), viewModel.uiState.value.picker?.selection?.keys)
        viewModel.togglePick(PantryEntry("sal", null))

        assertEquals(emptySet<String>(), viewModel.uiState.value.picker?.selection?.keys)
    }

    @Test
    fun `confirming adds the selection and closes the picker`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.openPicker()
        viewModel.togglePick(PantryEntry("Sal", "Especias y hierbas"))
        viewModel.togglePick(PantryEntry("Papel de cocina", null))

        viewModel.confirmPicker()
        advanceUntilIdle()

        assertEquals(
            listOf(PantryEntry("Sal", "Especias y hierbas"), PantryEntry("Papel de cocina", null)),
            pantry.added.single(),
        )
        assertNull(viewModel.uiState.value.picker)
        assertEquals(PantryUiMessage.ItemsAdded, viewModel.uiState.value.save.notice)
    }

    @Test
    fun `a failed add keeps the picker open with its selection`() = runTest(mainDispatcherRule.testDispatcher) {
        pantry.addFailure = PantryRepositoryException(PantryFailure.Network)
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.openPicker()
        viewModel.togglePick(PantryEntry("Sal", "Especias y hierbas"))

        viewModel.confirmPicker()
        advanceUntilIdle()

        assertEquals(PantryUiMessage.NetworkError, viewModel.uiState.value.save.error)
        assertEquals(setOf("sal"), viewModel.uiState.value.picker?.selection?.keys)
    }

    @Test
    fun `a retry that works after a failed add leaves no stale error`() = runTest(mainDispatcherRule.testDispatcher) {
        pantry.addFailure = PantryRepositoryException(PantryFailure.Network)
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.openPicker()
        viewModel.togglePick(PantryEntry("Sal", "Especias y hierbas"))
        viewModel.confirmPicker()
        advanceUntilIdle()
        assertEquals(PantryUiMessage.NetworkError, viewModel.uiState.value.save.error)

        pantry.addFailure = null
        viewModel.confirmPicker()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.picker)
        assertNull(viewModel.uiState.value.save.error)
        assertEquals(PantryUiMessage.ItemsAdded, viewModel.uiState.value.save.notice)
    }

    @Test
    fun `confirming with nothing selected writes nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.openPicker()

        viewModel.confirmPicker()
        advanceUntilIdle()

        assertTrue(pantry.added.isEmpty())
    }

    @Test
    fun `a restock failure keeps a pending notice after the error is shown`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.openPicker()
            viewModel.togglePick(PantryEntry("Sal", "Especias y hierbas"))
            viewModel.confirmPicker()
            advanceUntilIdle()
            shopping.itemsState.value = listOf(shoppingItem("s1", "Tomate", purchased = true))
            shopping.applyFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)

            viewModel.restock(tomato)
            advanceUntilIdle()
            viewModel.feedback.consumeError()

            assertNull(viewModel.uiState.value.save.error)
            assertEquals(PantryUiMessage.ItemsAdded, viewModel.uiState.value.save.notice)
        }

    @Test
    fun `confirming the picker while another action is saving keeps the picker error and writes nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            pantry.itemsState.value = listOf(salt)
            viewModel.observe("home")
            viewModel.openPicker()
            viewModel.togglePick(PantryEntry("Sal", "Especias y hierbas"))
            pantry.addFailure = PantryRepositoryException(PantryFailure.Network)
            viewModel.confirmPicker()
            advanceUntilIdle()
            assertEquals(PantryUiMessage.NetworkError, viewModel.uiState.value.save.error)
            pantry.addFailure = null
            pantry.removeGate = CompletableDeferred()
            viewModel.markFinished(salt)
            runCurrent()
            val addedBefore = pantry.added.size

            viewModel.confirmPicker()
            runCurrent()

            assertEquals(PantryUiMessage.NetworkError, viewModel.uiState.value.save.error)
            assertEquals(addedBefore, pantry.added.size)
            pantry.removeGate?.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `restock never turns saving on`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.restock(tomato)
        runCurrent()

        assertEquals(false, viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `every pantry and shopping failure keeps its message`() = runTest(mainDispatcherRule.testDispatcher) {
        val pantryExpected = mapOf(
            PantryFailure.NameRequired to PantryUiMessage.NameRequired,
            PantryFailure.NameTooLong to PantryUiMessage.NameTooLong,
            PantryFailure.NotAuthenticated to PantryUiMessage.NotAuthenticated,
            PantryFailure.EmailNotVerified to PantryUiMessage.EmailNotVerified,
            PantryFailure.SpaceNotFound to PantryUiMessage.SpaceNotFound,
            PantryFailure.PermissionDenied to PantryUiMessage.PermissionDenied,
            PantryFailure.Network to PantryUiMessage.NetworkError,
            PantryFailure.Unknown to PantryUiMessage.UnexpectedError,
        )
        assertEquals(PantryFailure.entries.toSet(), pantryExpected.keys)
        pantry.itemsState.value = listOf(tomato)
        viewModel.observe("home")
        advanceUntilIdle()
        pantryExpected.forEach { (failure, message) ->
            pantry.removeFailure = PantryRepositoryException(failure)
            viewModel.markFinished(tomato)
            advanceUntilIdle()
            assertEquals("$failure", message, viewModel.uiState.value.save.error)
            viewModel.feedback.consumeError()
        }

        val shoppingExpected = mapOf(
            ShoppingFailure.NameRequired to PantryUiMessage.UnexpectedError,
            ShoppingFailure.NameTooLong to PantryUiMessage.UnexpectedError,
            ShoppingFailure.QuantityTooLong to PantryUiMessage.UnexpectedError,
            ShoppingFailure.NotesTooLong to PantryUiMessage.UnexpectedError,
            ShoppingFailure.BrandTooLong to PantryUiMessage.UnexpectedError,
            ShoppingFailure.NotAuthenticated to PantryUiMessage.NotAuthenticated,
            ShoppingFailure.EmailNotVerified to PantryUiMessage.EmailNotVerified,
            ShoppingFailure.SpaceNotFound to PantryUiMessage.SpaceNotFound,
            ShoppingFailure.ItemNotFound to PantryUiMessage.ShoppingChanged,
            ShoppingFailure.ItemChanged to PantryUiMessage.ShoppingChanged,
            ShoppingFailure.CreatedItemChanged to PantryUiMessage.UnexpectedError,
            ShoppingFailure.PermissionDenied to PantryUiMessage.PermissionDenied,
            ShoppingFailure.Network to PantryUiMessage.NetworkError,
            ShoppingFailure.Unknown to PantryUiMessage.UnexpectedError,
        )
        assertEquals(ShoppingFailure.entries.toSet(), shoppingExpected.keys)
        shopping.itemsState.value = listOf(shoppingItem("s1", "Tomate", purchased = true))
        shoppingExpected.forEach { (failure, message) ->
            shopping.applyFailure = ShoppingRepositoryException(failure)
            viewModel.restock(tomato)
            advanceUntilIdle()
            assertEquals("$failure", message, viewModel.uiState.value.save.error)
            viewModel.feedback.consumeError()
        }
    }
}
