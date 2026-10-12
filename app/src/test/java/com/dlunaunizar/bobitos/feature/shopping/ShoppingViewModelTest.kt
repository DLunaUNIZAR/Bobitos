package com.dlunaunizar.bobitos.feature.shopping

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.repository.IngredientFailure
import com.dlunaunizar.bobitos.data.repository.IngredientRepositoryException
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.feature.common.ingredientShoppingSource
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientBrandRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientPrefsRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakePersonalIngredientRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeShoppingRepository()
    private val ingredientRepository = FakeIngredientRepository()
    private val personalRepository = FakePersonalIngredientRepository()
    private val brandRepository = FakeIngredientBrandRepository()
    private val prefsRepository = FakeIngredientPrefsRepository()
    private val viewModel = ShoppingViewModel(
        repository,
        ingredientRepository,
        personalRepository,
        ingredientShoppingSource(ingredientRepository, brandRepository, prefsRepository, personalRepository),
    )

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
        viewModel.addItem("home", "  Leche  ", " 2 ", "  ", Supermarket.MERCADONA, "Hacendado")
        advanceUntilIdle()

        assertEquals("Leche", repository.addedName)
        assertEquals("2", repository.addedQuantity)
        assertNull(repository.addedNotes)
        assertEquals(Supermarket.MERCADONA, repository.addedSupermarket)
        assertEquals("Hacendado", repository.addedBrand)
        assertEquals(ShoppingUiMessage.ItemAdded, viewModel.uiState.value.notice)
        assertEquals(ShoppingWriteStatus.SAVED, viewModel.uiState.value.writeStatus)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `manual add no longer applies any preference`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addItem("home", "Pan", null, null, null, null)
        advanceUntilIdle()

        assertEquals("Pan", repository.addedName)
        assertNull(repository.addedSupermarket)
        assertNull(repository.addedBrand)
    }

    @Test
    fun `creating an ingredient from an item creates it as personal`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.createIngredientFromItem(shoppingItem())
        advanceUntilIdle()

        assertEquals(0, ingredientRepository.createCount)
        assertEquals(1, personalRepository.createCount)
        assertEquals("Leche", personalRepository.lastCreatedName)
        assertEquals(ShoppingUiMessage.IngredientCreated, viewModel.uiState.value.notice)
    }

    @Test
    fun `creating an ingredient that already exists shows a notice and does not write`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogState.value = listOf(catalogIngredient())

            viewModel.createIngredientFromItem(shoppingItem())
            advanceUntilIdle()

            assertEquals(0, personalRepository.createCount)
            assertEquals(ShoppingUiMessage.IngredientExists, viewModel.uiState.value.notice)
            assertFalse(viewModel.uiState.value.isSaving)
        }

    @Test
    fun `creating an ingredient whose key matches a common one shows a notice and does not write`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogState.value = listOf(CatalogIngredient(id = "tomate", name = "Tomate"))

            viewModel.createIngredientFromItem(shoppingItem().copy(name = "Tomates"))
            advanceUntilIdle()

            assertEquals(0, personalRepository.createCount)
            assertEquals(ShoppingUiMessage.IngredientExists, viewModel.uiState.value.notice)
        }

    @Test
    fun `creating an ingredient whose key matches a personal one shows a notice and does not write`() =
        runTest(mainDispatcherRule.testDispatcher) {
            personalRepository.state.value = listOf(CatalogIngredient(id = "salsa", name = "Salsa"))

            viewModel.createIngredientFromItem(shoppingItem().copy(name = "Salsas"))
            advanceUntilIdle()

            assertEquals(0, personalRepository.createCount)
            assertEquals(ShoppingUiMessage.IngredientExists, viewModel.uiState.value.notice)
        }

    @Test
    fun `creating an ingredient rejected as already existing shows the exists notice`() =
        runTest(mainDispatcherRule.testDispatcher) {
            personalRepository.createError = IngredientRepositoryException(IngredientFailure.AlreadyExists)

            viewModel.createIngredientFromItem(shoppingItem())
            advanceUntilIdle()

            assertEquals(ShoppingUiMessage.IngredientExists, viewModel.uiState.value.notice)
            assertNull(viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isSaving)
        }

    @Test
    fun `creating an ingredient with a network failure shows the shopping network error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            personalRepository.createError = IngredientRepositoryException(IngredientFailure.Network)

            viewModel.createIngredientFromItem(shoppingItem())
            advanceUntilIdle()

            assertEquals(ShoppingUiMessage.NetworkError, viewModel.uiState.value.error)
        }

    @Test
    fun `creating an ingredient when the common catalog fails does not write and reports network`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogOverride =
                flow { throw IngredientRepositoryException(IngredientFailure.Network) }

            viewModel.createIngredientFromItem(shoppingItem())
            advanceUntilIdle()

            assertEquals(0, personalRepository.createCount)
            assertEquals(ShoppingUiMessage.NetworkError, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isSaving)
        }

    @Test
    fun `a permission error loading ingredients is not reported as network`() =
        runTest(mainDispatcherRule.testDispatcher) {
            personalRepository.personalOverride =
                flow { throw IngredientRepositoryException(IngredientFailure.PermissionDenied) }

            viewModel.createIngredientFromItem(shoppingItem())
            advanceUntilIdle()

            assertEquals(0, personalRepository.createCount)
            assertEquals(ShoppingUiMessage.PermissionDenied, viewModel.uiState.value.error)
        }

    @Test
    fun `ingredient assist exposes ingredients, brands and prefs`() = runTest(mainDispatcherRule.testDispatcher) {
        ingredientRepository.catalogState.value = listOf(catalogIngredient())
        val brand = IngredientBrand(id = "b1", ingredientId = "leche", name = "Pascual")
        brandRepository.brandsState.value = listOf(brand)
        prefsRepository.state.value = mapOf("leche" to IngredientPref(listOf(Supermarket.MERCADONA)))

        viewModel.startIngredientAssist()
        advanceUntilIdle()

        val data = viewModel.uiState.value.ingredientData
        assertEquals(listOf("Leche"), data.ingredients.map(CatalogIngredient::name))
        assertEquals(listOf(brand), data.brands)
        assertEquals(listOf(Supermarket.MERCADONA), data.prefs["leche"]?.stores)
    }

    @Test
    fun `restarting the ingredient assist drops stale data`() = runTest(mainDispatcherRule.testDispatcher) {
        ingredientRepository.catalogState.value = listOf(catalogIngredient())
        viewModel.startIngredientAssist()
        advanceUntilIdle()
        viewModel.stopIngredientAssist()
        // La nueva recogida aún no ha emitido: no debe verse lo viejo.
        ingredientRepository.catalogOverride = flow { awaitCancellation() }

        viewModel.startIngredientAssist()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.ingredientData.ingredients.isEmpty())
    }

    @Test
    fun `a personal ingredient shadowed by a common one with the same id is suggested once`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogState.value = listOf(CatalogIngredient(id = "tomate", name = "Tomate"))
            personalRepository.state.value =
                listOf(CatalogIngredient(id = "tomate", name = "Tomate", personal = true))

            viewModel.startIngredientAssist()
            advanceUntilIdle()

            assertEquals(
                listOf("Tomate"),
                viewModel.uiState.value.ingredientData.ingredients.map(CatalogIngredient::name),
            )
            assertFalse(viewModel.uiState.value.ingredientData.ingredients.single().personal)
        }

    @Test
    fun `creating an ingredient when the common catalog never emits times out without writing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogOverride = flow { awaitCancellation() }

            viewModel.createIngredientFromItem(shoppingItem())
            advanceTimeBy(29_999)
            runCurrent()
            assertNull(viewModel.uiState.value.error)
            advanceTimeBy(1)
            runCurrent()

            assertEquals(0, personalRepository.createCount)
            assertEquals(ShoppingUiMessage.NetworkError, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isSaving)
        }

    @Test
    fun `invalid product never reaches repository`() {
        viewModel.addItem("home", " ", null, null, null, null)

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

    @Test
    fun `editor save is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate

        viewModel.addItem("home", "Leche", null, null, null, null)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        assertTrue(viewModel.uiState.value.isSaving)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
        assertFalse(viewModel.uiState.value.isSaving)
        viewModel.consumeEditorSave()
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
    }

    @Test
    fun `editor save failure or timeout leaves FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.nextFailure = ShoppingRepositoryException(ShoppingFailure.Network)
        viewModel.addItem("home", "Leche", null, null, null, null)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(ShoppingUiMessage.NetworkError, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)

        repository.nextFailure = null
        viewModel.consumeEditorSave()
        repository.hang = true
        viewModel.addItem("home", "Leche", null, null, null, null)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(ShoppingUiMessage.SaveTimeout, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `editor update also moves the editor status`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.updateItem("home", "milk", "Leche", "2", null, null, null)
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
    }

    @Test
    fun `restoreItem does not touch the editor status`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.restoreItem("home", shoppingItem())
        advanceUntilIdle()

        assertEquals("Leche", repository.addedName)
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
        assertEquals(ShoppingUiMessage.ItemAdded, viewModel.uiState.value.notice)
    }

    @Test
    fun `adding a product already pending asks instead of writing`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.itemState.value = listOf(listItem(quantity = "1 l"))
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addItem("home", " leches ", " 2 l ", null, null, null)
        advanceUntilIdle()

        assertEquals(ShoppingDuplicate(listItem(quantity = "1 l"), "2 l"), viewModel.uiState.value.duplicate)
        assertNull(repository.addedName)
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
    }

    @Test
    fun `summing a duplicate updates only the quantity of the existing item`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.itemState.value = listOf(listItem(quantity = "1 l"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addItem("home", "Leche", "2 l", null, Supermarket.EROSKI, "Otra")
            advanceUntilIdle()

            viewModel.sumDuplicate("home")
            advanceUntilIdle()

            assertEquals("milk", repository.updatedItemId)
            assertEquals("3 l", repository.updatedQuantity)
            assertEquals(Supermarket.DIA, repository.updatedSupermarket)
            assertEquals("Pascual", repository.updatedBrand)
            assertNull(viewModel.uiState.value.duplicate)
            assertEquals(ShoppingUiMessage.ItemUpdated, viewModel.uiState.value.notice)
            assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
        }

    @Test
    fun `dismissing a duplicate writes nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.itemState.value = listOf(listItem(quantity = "1 l"))
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.addItem("home", "Leche", "2 l", null, null, null)
        advanceUntilIdle()

        viewModel.dismissDuplicate()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.duplicate)
        assertNull(repository.addedName)
        assertNull(repository.updatedItemId)
    }

    @Test
    fun `adding a purchased product relists it with the editor values and keeps its name`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.itemState.value = listOf(listItem(quantity = "6 l", purchased = true))
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.addItem("home", " leches ", " 2 ", " sin lactosa ", Supermarket.EROSKI, "Puleva")
            advanceUntilIdle()

            assertEquals(
                RelistCall("milk", "Leche", "2", "sin lactosa", Supermarket.EROSKI, "Puleva"),
                repository.relisted,
            )
            assertNull(repository.addedName)
            assertNull(viewModel.uiState.value.duplicate)
            assertEquals(ShoppingUiMessage.ItemAdded, viewModel.uiState.value.notice)
            assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
        }

    @Test
    fun `a sum longer than 40 characters is reported and not written`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.itemState.value = listOf(listItem(quantity = "x".repeat(30)))
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.addItem("home", "Leche", "y".repeat(10), null, null, null)
        advanceUntilIdle()

        viewModel.sumDuplicate("home")
        advanceUntilIdle()

        assertNull(repository.updatedItemId)
        assertEquals(ShoppingUiMessage.QuantityTooLong, viewModel.uiState.value.error)
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
    }
}

private class FakeShoppingRepository : ShoppingRepository {
    var addGate: CompletableDeferred<Unit>? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    var observedSpaceId: String? = null
    var addedName: String? = null
    var addedQuantity: String? = null
    var addedNotes: String? = null
    var addedSupermarket: Supermarket? = null
    var addedBrand: String? = null
    var updatedSupermarket: Supermarket? = null
    var updatedBrand: String? = null
    var updatedItemId: String? = null
    var updatedQuantity: String? = null
    var relisted: RelistCall? = null
    var purchaseChange: Triple<String, String, Boolean>? = null
    var clearCount = 0
    var nextFailure: ShoppingRepositoryException? = null
    val itemState = MutableStateFlow(listOf(shoppingItem()))

    override fun items(spaceId: String): Flow<List<ShoppingItem>> {
        observedSpaceId = spaceId
        return itemState
    }

    override suspend fun addItem(
        spaceId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) {
        throwNextFailure()
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                throw ShoppingRepositoryException(ShoppingFailure.Unknown)
            }
        }
        addGate?.await()
        addedName = name
        addedQuantity = quantity
        addedNotes = notes
        addedSupermarket = supermarket
        addedBrand = brand
    }

    override suspend fun updateItem(
        spaceId: String,
        itemId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) {
        throwNextFailure()
        updatedItemId = itemId
        updatedQuantity = quantity
        updatedSupermarket = supermarket
        updatedBrand = brand
    }

    override suspend fun relistItem(
        spaceId: String,
        itemId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) {
        throwNextFailure()
        relisted = RelistCall(itemId, name, quantity, notes, supermarket, brand)
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

private fun listItem(quantity: String? = null, purchased: Boolean = false) = shoppingItem().copy(
    quantity = quantity,
    purchased = purchased,
    supermarket = Supermarket.DIA,
    brand = "Pascual",
)

private data class RelistCall(
    val itemId: String,
    val name: String,
    val quantity: String?,
    val notes: String?,
    val supermarket: Supermarket?,
    val brand: String?,
)

private fun catalogIngredient() = CatalogIngredient(id = "leche", name = "Leche")
