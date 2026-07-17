package com.dlunaunizar.bobitos.feature.shopping

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeShoppingRepository()
    private val viewModel = ShoppingViewModel(repository)

    @Test
    fun `observes items for active space`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        val items = (viewModel.uiState.value.items as UiState.Content).value
        assertEquals("home", repository.observedSpaceId)
        assertEquals("milk", items.single().id)
    }

    @Test
    fun `add trims values and reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.addItem("home", "  Leche  ", " 2 ", "  ")
        advanceUntilIdle()

        assertEquals("Leche", repository.addedName)
        assertEquals("2", repository.addedQuantity)
        assertNull(repository.addedNotes)
        assertEquals(ShoppingUiMessage.ItemAdded, viewModel.uiState.value.notice)
        assertEquals(ShoppingWriteStatus.SAVED, viewModel.uiState.value.writeStatus)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `invalid product never reaches repository`() {
        viewModel.addItem("home", " ", null, null)

        assertNull(repository.addedName)
        assertEquals(ShoppingUiMessage.NameRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `marking delegates without assigning a responsible user`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.setPurchased("home", "milk", true)
        advanceUntilIdle()

        assertEquals(Triple("home", "milk", true), repository.purchaseChange)
        assertEquals(ShoppingUiMessage.ItemMarked, viewModel.uiState.value.notice)
    }

    @Test
    fun `safe cleanup exposes actual deleted count`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.clearCount = 2

        viewModel.clearPurchased("home")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.lastClearedCount)
        assertEquals(ShoppingUiMessage.PurchasedCleared, viewModel.uiState.value.notice)
    }

    @Test
    fun `network failure is shown explicitly`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.nextFailure = ShoppingRepositoryException(ShoppingFailure.Network)

        viewModel.deleteItem("home", "milk")
        advanceUntilIdle()

        assertEquals(ShoppingUiMessage.NetworkError, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}

private class FakeShoppingRepository : ShoppingRepository {
    var observedSpaceId: String? = null
    var addedName: String? = null
    var addedQuantity: String? = null
    var addedNotes: String? = null
    var purchaseChange: Triple<String, String, Boolean>? = null
    var clearCount = 0
    var nextFailure: ShoppingRepositoryException? = null
    private val itemState = MutableStateFlow(listOf(shoppingItem()))

    override fun items(spaceId: String): Flow<List<ShoppingItem>> {
        observedSpaceId = spaceId
        return itemState
    }

    override suspend fun addItem(spaceId: String, name: String, quantity: String?, notes: String?) {
        throwNextFailure()
        addedName = name
        addedQuantity = quantity
        addedNotes = notes
    }

    override suspend fun updateItem(spaceId: String, itemId: String, name: String, quantity: String?, notes: String?) {
        throwNextFailure()
    }

    override suspend fun setPurchased(spaceId: String, itemId: String, purchased: Boolean) {
        throwNextFailure()
        purchaseChange = Triple(spaceId, itemId, purchased)
    }

    override suspend fun deleteItem(spaceId: String, itemId: String) {
        throwNextFailure()
    }

    override suspend fun clearPurchased(spaceId: String): Int {
        throwNextFailure()
        return clearCount
    }

    private fun throwNextFailure() {
        nextFailure?.let { throw it }
    }
}

private fun shoppingItem() = ShoppingItem(
    id = "milk",
    name = "Leche",
    quantity = null,
    notes = null,
    purchased = false,
    createdBy = "owner",
    createdByName = "David",
    createdAt = Instant.EPOCH,
    updatedBy = "owner",
    updatedAt = Instant.EPOCH,
    purchasedBy = null,
    purchasedByName = null,
    purchasedAt = null,
)
