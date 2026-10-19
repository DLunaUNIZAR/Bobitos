package com.dlunaunizar.bobitos.feature.shopping

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.core.model.PantryItem
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.repository.IngredientFailure
import com.dlunaunizar.bobitos.data.repository.IngredientRepositoryException
import com.dlunaunizar.bobitos.data.repository.PantryFailure
import com.dlunaunizar.bobitos.data.repository.PantryRepositoryException
import com.dlunaunizar.bobitos.data.repository.ShoppingCrossOff
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.data.repository.ShoppingWrite
import com.dlunaunizar.bobitos.feature.common.FakePantryRepository
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
    private val pantryRepository = FakePantryRepository()
    private val viewModel by lazy {
        ShoppingViewModel(
            repository,
            ingredientRepository,
            personalRepository,
            ingredientShoppingSource(ingredientRepository, brandRepository, prefsRepository, personalRepository),
            pantryRepository,
        )
    }

    @Test
    fun `the editor assist listens to the pantry of the space only while it is open`() =
        runTest(mainDispatcherRule.testDispatcher) {
            pantryRepository.itemsState.value = listOf(PantryItem("tomat", "Tomate", null, null))
            viewModel.observe("home")
            advanceUntilIdle()
            assertEquals(emptySet<String>(), viewModel.uiState.value.pantryIds)
            assertNull(pantryRepository.observedSpaceId)

            viewModel.startIngredientAssist()
            advanceUntilIdle()
            assertEquals("home", pantryRepository.observedSpaceId)
            assertEquals(setOf("tomat"), viewModel.uiState.value.pantryIds)

            viewModel.stopIngredientAssist()
            pantryRepository.itemsState.value = emptyList()
            advanceUntilIdle()
            assertEquals(setOf("tomat"), viewModel.uiState.value.pantryIds)
        }

    @Test
    fun `a pantry that cannot be read gives no hint and no error`() = runTest(mainDispatcherRule.testDispatcher) {
        pantryRepository.itemsFailure = PantryRepositoryException(PantryFailure.PermissionDenied)
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.startIngredientAssist()
        advanceUntilIdle()

        assertEquals(emptySet<String>(), viewModel.uiState.value.pantryIds)
        assertNull(viewModel.uiState.value.save.error)
    }

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
        repository.itemState.value = emptyList()
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.addItem("home", "  Leche  ", " 2 ", "  ", Supermarket.MERCADONA, "Hacendado")
        advanceUntilIdle()

        assertEquals("Leche", repository.addedName)
        assertEquals("2", repository.addedQuantity)
        assertNull(repository.addedNotes)
        assertEquals(Supermarket.MERCADONA, repository.addedSupermarket)
        assertEquals("Hacendado", repository.addedBrand)
        assertNull(viewModel.uiState.value.save.notice)
        assertEquals(CreatedItem("new-1", "Leche"), viewModel.uiState.value.save.created)
        assertFalse(viewModel.uiState.value.save.isSaving)
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
        assertEquals(ShoppingUiMessage.IngredientCreated, viewModel.uiState.value.save.notice)
    }

    @Test
    fun `creating an ingredient that already exists shows a notice and does not write`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogState.value = listOf(catalogIngredient())

            viewModel.createIngredientFromItem(shoppingItem())
            advanceUntilIdle()

            assertEquals(0, personalRepository.createCount)
            assertEquals(ShoppingUiMessage.IngredientExists, viewModel.uiState.value.save.notice)
            assertFalse(viewModel.uiState.value.save.isSaving)
        }

    @Test
    fun `creating an ingredient whose key matches a common one shows a notice and does not write`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogState.value = listOf(CatalogIngredient(id = "tomate", name = "Tomate"))

            viewModel.createIngredientFromItem(shoppingItem().copy(name = "Tomates"))
            advanceUntilIdle()

            assertEquals(0, personalRepository.createCount)
            assertEquals(ShoppingUiMessage.IngredientExists, viewModel.uiState.value.save.notice)
        }

    @Test
    fun `creating an ingredient whose key matches a personal one shows a notice and does not write`() =
        runTest(mainDispatcherRule.testDispatcher) {
            personalRepository.state.value = listOf(CatalogIngredient(id = "salsa", name = "Salsa"))

            viewModel.createIngredientFromItem(shoppingItem().copy(name = "Salsas"))
            advanceUntilIdle()

            assertEquals(0, personalRepository.createCount)
            assertEquals(ShoppingUiMessage.IngredientExists, viewModel.uiState.value.save.notice)
        }

    @Test
    fun `creating an ingredient rejected as already existing shows the exists notice`() =
        runTest(mainDispatcherRule.testDispatcher) {
            personalRepository.createError = IngredientRepositoryException(IngredientFailure.AlreadyExists)

            viewModel.createIngredientFromItem(shoppingItem())
            advanceUntilIdle()

            assertEquals(ShoppingUiMessage.IngredientExists, viewModel.uiState.value.save.notice)
            assertNull(viewModel.uiState.value.save.error)
            assertFalse(viewModel.uiState.value.save.isSaving)
        }

    @Test
    fun `creating an ingredient with a network failure shows the shopping network error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            personalRepository.createError = IngredientRepositoryException(IngredientFailure.Network)

            viewModel.createIngredientFromItem(shoppingItem())
            advanceUntilIdle()

            assertEquals(ShoppingUiMessage.NetworkError, viewModel.uiState.value.save.error)
        }

    @Test
    fun `creating an ingredient when the common catalog fails does not write and reports network`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogOverride =
                flow { throw IngredientRepositoryException(IngredientFailure.Network) }

            viewModel.createIngredientFromItem(shoppingItem())
            advanceUntilIdle()

            assertEquals(0, personalRepository.createCount)
            assertEquals(ShoppingUiMessage.NetworkError, viewModel.uiState.value.save.error)
            assertFalse(viewModel.uiState.value.save.isSaving)
        }

    @Test
    fun `a permission error loading ingredients is not reported as network`() =
        runTest(mainDispatcherRule.testDispatcher) {
            personalRepository.personalOverride =
                flow { throw IngredientRepositoryException(IngredientFailure.PermissionDenied) }

            viewModel.createIngredientFromItem(shoppingItem())
            advanceUntilIdle()

            assertEquals(0, personalRepository.createCount)
            assertEquals(ShoppingUiMessage.PermissionDenied, viewModel.uiState.value.save.error)
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
            assertNull(viewModel.uiState.value.save.error)
            advanceTimeBy(1)
            runCurrent()

            assertEquals(0, personalRepository.createCount)
            assertEquals(ShoppingUiMessage.NetworkError, viewModel.uiState.value.save.error)
            assertFalse(viewModel.uiState.value.save.isSaving)
        }

    @Test
    fun `invalid product never reaches repository`() {
        viewModel.addItem("home", " ", null, null, null, null)

        assertNull(repository.addedName)
        assertEquals(ShoppingUiMessage.NameRequired, viewModel.uiState.value.save.editorError)
    }

    @Test
    fun `marking delegates without assigning a responsible user`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.setPurchased("home", "milk", true)
        advanceUntilIdle()

        assertEquals(Triple("home", "milk", true), repository.purchaseChange)
        assertEquals(ShoppingUiMessage.ItemMarked, viewModel.uiState.value.save.notice)
    }

    @Test
    fun marcarTresProductosSeguidosEscribeLosTresAlMomento() = runTest(mainDispatcherRule.testDispatcher) {
        repository.purchaseGate = CompletableDeferred()

        assertTrue(viewModel.setPurchased("home", "milk", true))
        assertTrue(viewModel.setPurchased("home", "bread", true))
        assertTrue(viewModel.setPurchased("home", "salt", true))
        runCurrent()

        // Las tres escrituras están en curso a la vez y no apagan el resto de la pantalla.
        assertEquals(listOf("milk", "bread", "salt"), repository.purchaseStarted)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun marcarElMismoProductoDosVecesMientrasEscribeSoloEscribeUna() = runTest(mainDispatcherRule.testDispatcher) {
        repository.purchaseGate = CompletableDeferred()

        assertTrue(viewModel.setPurchased("home", "milk", true))
        assertFalse(viewModel.setPurchased("home", "milk", true))
        runCurrent()

        assertEquals(listOf("milk"), repository.purchaseStarted)
    }

    @Test
    fun marcarAvisaDeSuResultado() = runTest(mainDispatcherRule.testDispatcher) {
        val resultados = mutableListOf<Boolean>()
        repository.nextFailure = ShoppingRepositoryException(ShoppingFailure.Network)

        viewModel.setPurchased("home", "milk", true) { resultados += it }
        advanceUntilIdle()
        repository.nextFailure = null
        viewModel.setPurchased("home", "milk", true) { resultados += it }
        advanceUntilIdle()

        assertEquals(listOf(false, true), resultados)
        assertEquals(ShoppingUiMessage.ItemMarked, viewModel.uiState.value.save.notice)
    }

    @Test
    fun unaEscrituraCanceladaLiberaElProductoYAvisaDeQueNoTermino() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.purchaseGate = gate
        val resultados = mutableListOf<Boolean>()

        assertTrue(viewModel.setPurchased("home", "milk", true) { resultados += it })
        runCurrent()
        gate.cancel()
        advanceUntilIdle()

        // La cancelación no cuenta como éxito y el producto queda libre para el siguiente toque.
        assertEquals(listOf(false), resultados)
        repository.purchaseGate = null
        assertTrue(viewModel.setPurchased("home", "milk", true) { resultados += it })
        advanceUntilIdle()
        assertEquals(Triple("home", "milk", true), repository.purchaseChange)
        assertEquals(listOf(false, true), resultados)
    }

    @Test
    fun `safe cleanup exposes actual deleted count`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.clearCount = 2

        viewModel.clearPurchased("home")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.lastClearedCount)
        assertEquals(ShoppingUiMessage.PurchasedCleared, viewModel.uiState.value.save.notice)
    }

    @Test
    fun `restoring an item while another write is in flight keeps saving until both end`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val deleteGate = CompletableDeferred<Unit>()
            val restoreGate = CompletableDeferred<Unit>()
            repository.deleteGate = deleteGate
            repository.restoreGate = restoreGate

            viewModel.deleteItem("home", "milk")
            runCurrent()
            viewModel.restoreItem("home", shoppingItem())
            runCurrent()
            assertTrue(viewModel.uiState.value.save.isSaving)

            restoreGate.complete(Unit)
            runCurrent()
            assertTrue(viewModel.uiState.value.save.isSaving)

            deleteGate.complete(Unit)
            runCurrent()
            assertFalse(viewModel.uiState.value.save.isSaving)
        }

    @Test
    fun `marking an item bought does not turn saving on`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.purchaseGate = CompletableDeferred()

        assertTrue(viewModel.setPurchased("home", "milk", true))
        runCurrent()

        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `a duplicate ingredient ends with the IngredientExists notice and no error`() =
        runTest(mainDispatcherRule.testDispatcher) {
            personalRepository.createError = IngredientRepositoryException(IngredientFailure.AlreadyExists)

            viewModel.createIngredientFromItem(shoppingItem())
            advanceUntilIdle()

            assertEquals(ShoppingUiMessage.IngredientExists, viewModel.uiState.value.save.notice)
            assertNull(viewModel.uiState.value.save.error)
            assertFalse(viewModel.uiState.value.save.isSaving)
        }

    @Test
    fun `network failure is shown explicitly`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.nextFailure = ShoppingRepositoryException(ShoppingFailure.Network)

        viewModel.deleteItem("home", "milk")
        advanceUntilIdle()

        assertEquals(ShoppingUiMessage.NetworkError, viewModel.uiState.value.save.error)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `editor save is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.itemState.value = emptyList()
        viewModel.observe("home")
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate

        viewModel.addItem("home", "Leche", null, null, null, null)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.save.editor)
        assertTrue(viewModel.uiState.value.save.isSaving)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.save.editor)
        assertFalse(viewModel.uiState.value.save.isSaving)
        viewModel.feedback.resetEditor()
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.save.editor)
    }

    @Test
    fun `editor save failure or timeout leaves FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.itemState.value = emptyList()
        viewModel.observe("home")
        advanceUntilIdle()
        repository.nextFailure = ShoppingRepositoryException(ShoppingFailure.Network)
        viewModel.addItem("home", "Leche", null, null, null, null)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        assertEquals(ShoppingUiMessage.NetworkError, viewModel.uiState.value.save.editorError)
        assertFalse(viewModel.uiState.value.save.isSaving)

        repository.nextFailure = null
        viewModel.feedback.resetEditor()
        repository.hang = true
        viewModel.addItem("home", "Leche", null, null, null, null)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.save.editor)
        advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        assertEquals(ShoppingUiMessage.SaveTimeout, viewModel.uiState.value.save.editorError)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `editor update also moves the editor status`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.updateItem("home", "milk", "Leche", "2", null, null, null)
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.save.editor)
    }

    @Test
    fun `undo is offered only once the delete has finished`() = runTest(mainDispatcherRule.testDispatcher) {
        var offered = 0
        repository.nextFailure = ShoppingRepositoryException(ShoppingFailure.Network)
        viewModel.deleteItem("home", "milk") { offered++ }
        advanceUntilIdle()
        assertEquals(0, offered)

        repository.nextFailure = null
        viewModel.deleteItem("home", "milk") { offered++ }
        advanceUntilIdle()
        assertEquals(1, offered)
        assertEquals(listOf("milk"), repository.deleted)
    }

    @Test
    fun `restoreItem does not touch the editor status`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.restoreItem("home", shoppingItem())
        advanceUntilIdle()

        assertEquals("Leche", repository.restored.single().name)
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.save.editor)
        assertNull(viewModel.uiState.value.save.created)
    }

    @Test
    fun `undoing a delete keeps the id and the purchased state`() = runTest(mainDispatcherRule.testDispatcher) {
        val bought = shoppingItem().copy(purchased = true, purchasedBy = "owner", purchasedByName = "David")

        viewModel.restoreItem("home", bought)
        advanceUntilIdle()

        assertEquals("home", repository.restoredSpaceId)
        assertEquals("milk", repository.restored.single().id)
        assertTrue(repository.restored.single().purchased)
        assertNull(repository.addedName)
    }

    @Test
    fun `a failed undo shows the error instead of staying silent`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.nextFailure = ShoppingRepositoryException(ShoppingFailure.PermissionDenied)

        viewModel.restoreItem("home", shoppingItem())
        advanceUntilIdle()

        assertEquals(ShoppingUiMessage.PermissionDenied, viewModel.uiState.value.save.error)
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
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.save.editor)
    }

    @Test
    fun `skipping a duplicate closes the editor and its failed state does not reach the screen`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.itemState.value = emptyList()
            viewModel.observe("home")
            advanceUntilIdle()
            repository.nextFailure = ShoppingRepositoryException(ShoppingFailure.Network)
            viewModel.addItem("home", "Leche", null, null, null, null)
            advanceUntilIdle()
            assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)

            viewModel.skipDuplicate()

            assertNull(viewModel.uiState.value.duplicate)
            assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.save.editor)
            assertNull(viewModel.uiState.value.save.editorError)
            assertNull(viewModel.uiState.value.save.error)
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
            assertEquals(ShoppingUiMessage.ItemUpdated, viewModel.uiState.value.save.notice)
            assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.save.editor)
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
            assertEquals(ShoppingUiMessage.ItemAdded, viewModel.uiState.value.save.notice)
            assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.save.editor)
        }

    @Test
    fun creatingOneItemOffersUndoWithItsIdAndName() = runTest(mainDispatcherRule.testDispatcher) {
        repository.itemState.value = emptyList()
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addItem("home", "Leche", null, null, null, null)
        advanceUntilIdle()

        assertEquals(CreatedItem("new-1", "Leche"), viewModel.uiState.value.save.created)
        assertNull(viewModel.uiState.value.save.notice)

        viewModel.feedback.consumeCreated()
        assertNull(viewModel.uiState.value.save.created)
    }

    @Test
    fun undoCreateDeletesExactlyThatItem() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        viewModel.undoCreate("new-1")
        advanceUntilIdle()

        assertEquals(listOf("new-1"), repository.deleted)
    }

    @Test
    fun undoWhileAnotherActionIsSavingStillDeletes() = runTest(mainDispatcherRule.testDispatcher) {
        repository.itemState.value = emptyList()
        viewModel.observe("home")
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate
        viewModel.addItem("home", "Leche", null, null, null, null)
        assertTrue(viewModel.uiState.value.save.isSaving)

        viewModel.undoCreate("new-1")
        advanceUntilIdle()

        assertEquals(listOf("new-1"), repository.deleted)
        gate.complete(Unit)
    }

    @Test
    fun editingDoesNotOfferUndo() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.updateItem("home", "milk", "Leche", "2", null, null, null)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.save.created)
    }

    @Test
    fun relistingAPurchasedItemNeverOffersUndo() = runTest(mainDispatcherRule.testDispatcher) {
        repository.itemState.value = listOf(listItem(quantity = "6 l", purchased = true))
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addItem("home", "Leche", "2", null, null, null)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.save.created)
        assertEquals(ShoppingUiMessage.ItemAdded, viewModel.uiState.value.save.notice)
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun summingADuplicateNeverOffersUndo() = runTest(mainDispatcherRule.testDispatcher) {
        repository.itemState.value = listOf(listItem(quantity = "1 l"))
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.addItem("home", "Leche", "2 l", null, null, null)
        advanceUntilIdle()

        viewModel.sumDuplicate("home")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.save.created)
        assertEquals(ShoppingUiMessage.ItemUpdated, viewModel.uiState.value.save.notice)
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun `summing a duplicate fills the empty notes, store and brand with the typed ones`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val pending = shoppingItem().copy(quantity = "1 l")
            repository.itemState.value = listOf(pending)
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addItem("home", "Leche", "2 l", " sin lactosa ", Supermarket.EROSKI, " Puleva ")
            advanceUntilIdle()

            viewModel.sumDuplicate("home")
            advanceUntilIdle()

            assertEquals(
                listOf(
                    ShoppingWrite.Change(pending, "3 l", "sin lactosa", Supermarket.EROSKI, "Puleva", relist = false),
                ),
                repository.applied,
            )
        }

    @Test
    fun `summing a duplicate keeps the notes, store and brand the item already has`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val pending = listItem(quantity = "1 l").copy(notes = "entera")
            repository.itemState.value = listOf(pending)
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addItem("home", "Leche", "2 l", "sin lactosa", Supermarket.EROSKI, "Otra")
            advanceUntilIdle()

            viewModel.sumDuplicate("home")
            advanceUntilIdle()

            assertEquals(
                listOf(ShoppingWrite.Change(pending, "3 l", "entera", Supermarket.DIA, "Pascual", relist = false)),
                repository.applied,
            )
        }

    @Test
    fun `summing a duplicate does not join a store and a brand that do not go together`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val pending = shoppingItem().copy(quantity = "1 l", supermarket = Supermarket.DIA)
            repository.itemState.value = listOf(pending)
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addItem("home", "Leche", "2 l", null, Supermarket.MERCADONA, "Hacendado")
            advanceUntilIdle()

            viewModel.sumDuplicate("home")
            advanceUntilIdle()

            assertEquals(
                listOf(ShoppingWrite.Change(pending, "3 l", null, Supermarket.DIA, null, relist = false)),
                repository.applied,
            )
        }

    @Test
    fun `summing treats an indifferent store as chosen and blank notes as empty`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val pending = shoppingItem().copy(quantity = "1 l", notes = "  ", supermarket = Supermarket.INDIFERENTE)
            repository.itemState.value = listOf(pending)
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addItem("home", "Leche", "2 l", "sin lactosa", Supermarket.EROSKI, null)
            advanceUntilIdle()

            viewModel.sumDuplicate("home")
            advanceUntilIdle()

            assertEquals(
                listOf(
                    ShoppingWrite.Change(pending, "3 l", "sin lactosa", Supermarket.INDIFERENTE, null, relist = false),
                ),
                repository.applied,
            )
        }

    @Test
    fun `a sum that does not fit keeps the notice open and writes nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.itemState.value = listOf(listItem(quantity = "x".repeat(30)))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addItem("home", "Leche", "y".repeat(10), null, null, null)
            advanceUntilIdle()

            viewModel.sumDuplicate("home")
            advanceUntilIdle()

            assertNull(repository.applied)
            assertTrue(viewModel.uiState.value.duplicate?.sumTooLong == true)
            assertNull(viewModel.uiState.value.save.error)
        }

    @Test
    fun `adding before the list is loaded asks to wait and writes nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.addItem("home", "Pan", "1", null, null, null)
            advanceUntilIdle()

            assertNull(repository.addedName)
            assertNull(repository.applied)
            assertEquals(ShoppingUiMessage.ListNotLoaded, viewModel.uiState.value.save.editorError)
            assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        }

    @Test
    fun `adding after the list failed to load says so, listens again and works once it loads`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.itemsFailures = 1
            viewModel.observe("home")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.items is UiState.Error)

            viewModel.addItem("home", "Pan", "1", null, null, null)
            advanceUntilIdle()

            assertNull(repository.addedName)
            assertEquals(ShoppingUiMessage.ListUnavailable, viewModel.uiState.value.save.editorError)
            assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
            assertEquals(2, repository.itemsCalls)
            assertTrue(viewModel.uiState.value.items is UiState.Content)

            viewModel.addItem("home", "Pan", "1", null, null, null)
            advanceUntilIdle()

            assertEquals("Pan", repository.addedName)
        }

    @Test
    fun `adding while the list keeps failing to load never writes and listens again each time`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.itemsFailures = 3
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.addItem("home", "Pan", "1", null, null, null)
            advanceUntilIdle()
            viewModel.addItem("home", "Pan", "1", null, null, null)
            advanceUntilIdle()

            assertNull(repository.addedName)
            assertNull(repository.applied)
            assertEquals(ShoppingUiMessage.ListUnavailable, viewModel.uiState.value.save.editorError)
            assertEquals(3, repository.itemsCalls)
            assertTrue(viewModel.uiState.value.items is UiState.Error)
        }

    @Test
    fun `a failed read of the list keeps no exception text and observing again loads it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.itemsFailures = 1
            viewModel.observe("home")
            advanceUntilIdle()

            assertEquals(UiState.Error(), viewModel.uiState.value.items)

            viewModel.observe("home")
            advanceUntilIdle()

            assertEquals(2, repository.itemsCalls)
            assertEquals("milk", (viewModel.uiState.value.items as UiState.Content).value.single().id)
        }

    @Test
    fun `a brand over 60 characters is refused in the editor before looking for repeats or writing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.observe("home")
            advanceUntilIdle()
            val longBrand = "M".repeat(61)

            viewModel.addItem("home", "Leche", "1", null, null, longBrand)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.duplicate)
            assertNull(repository.applied)
            assertEquals(ShoppingUiMessage.BrandTooLong, viewModel.uiState.value.save.editorError)
            assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)

            viewModel.addItem("home", "Pan", "1", null, null, longBrand)
            advanceUntilIdle()
            viewModel.updateItem("home", "milk", "Leche", "1", null, null, longBrand)
            advanceUntilIdle()

            assertNull(repository.addedName)
            assertNull(repository.updatedItemId)
            assertEquals(ShoppingUiMessage.BrandTooLong, viewModel.uiState.value.save.editorError)
        }

    @Test
    fun `relisting a purchased product sends the item as it was seen`() = runTest(mainDispatcherRule.testDispatcher) {
        val bought = listItem(quantity = "6 l", purchased = true)
        repository.itemState.value = listOf(bought)
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addItem("home", "Leche", "2", null, Supermarket.EROSKI, "Puleva")
        advanceUntilIdle()

        assertEquals(
            listOf(ShoppingWrite.Change(bought, "2", null, Supermarket.EROSKI, "Puleva", relist = true)),
            repository.applied,
        )
    }

    @Test
    fun `a product changed by another member meanwhile is reported and the editor stays open`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.itemState.value = listOf(listItem(quantity = "6 l", purchased = true))
            viewModel.observe("home")
            advanceUntilIdle()
            repository.nextFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)

            viewModel.addItem("home", "Leche", "2", null, null, null)
            advanceUntilIdle()

            assertEquals(ShoppingUiMessage.ItemChanged, viewModel.uiState.value.save.editorError)
            assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        }

    @Test
    fun `summing a duplicate sends the item as it was seen`() = runTest(mainDispatcherRule.testDispatcher) {
        val pending = listItem(quantity = "1 l")
        repository.itemState.value = listOf(pending)
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.addItem("home", "Leche", "2 l", null, null, null)
        advanceUntilIdle()

        viewModel.sumDuplicate("home")
        advanceUntilIdle()

        assertEquals(
            listOf(ShoppingWrite.Change(pending, "3 l", null, Supermarket.DIA, "Pascual", relist = false)),
            repository.applied,
        )
    }
}

internal class FakeShoppingRepository : ShoppingRepository {
    var addGate: CompletableDeferred<Unit>? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    var observedSpaceId: String? = null
    var addedName: String? = null
    val deleted = mutableListOf<String>()
    val restored = mutableListOf<ShoppingItem>()
    var restoredSpaceId: String? = null
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
    var purchaseEntry: PantryEntry? = null
    var clearCount = 0
    var nextFailure: ShoppingRepositoryException? = null
    val itemState = MutableStateFlow(listOf(shoppingItem()))
    var itemsFailures = 0
    var itemsCalls = 0

    // Las primeras `itemsFailures` escuchas fallan con un permiso denegado (como un listener que se cierra).
    override fun items(spaceId: String): Flow<List<ShoppingItem>> {
        observedSpaceId = spaceId
        itemsCalls++
        if (itemsFailures > 0) {
            itemsFailures--
            return flow {
                throw ShoppingRepositoryException(
                    ShoppingFailure.PermissionDenied,
                    IllegalStateException("PERMISSION_DENIED: evaluation error at L818"),
                )
            }
        }
        return itemState
    }

    override suspend fun currentItems(spaceId: String): List<ShoppingItem> = itemState.value

    override suspend fun addItem(
        spaceId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ): String {
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
        return "new-1"
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

    var applied: List<ShoppingWrite>? = null

    override suspend fun applyWrites(spaceId: String, writes: List<ShoppingWrite>) {
        throwNextFailure()
        applied = writes
        writes.forEach { write ->
            when (write) {
                is ShoppingWrite.Add -> {
                    addedName = write.name
                    addedQuantity = write.quantity
                    addedNotes = write.notes
                    addedSupermarket = write.supermarket
                    addedBrand = write.brand
                }
                is ShoppingWrite.Change -> if (write.relist) {
                    relisted = RelistCall(
                        write.seen.id,
                        write.seen.name,
                        write.quantity,
                        write.notes,
                        write.supermarket,
                        write.brand,
                    )
                } else {
                    updatedItemId = write.seen.id
                    updatedQuantity = write.quantity
                    updatedSupermarket = write.supermarket
                    updatedBrand = write.brand
                }
            }
        }
    }

    var purchaseGate: CompletableDeferred<Unit>? = null
    val purchaseStarted = mutableListOf<String>()

    override suspend fun setPurchased(spaceId: String, itemId: String, purchased: Boolean, pantryEntry: PantryEntry?) {
        purchaseStarted += itemId
        purchaseGate?.await()
        throwNextFailure()
        purchaseChange = Triple(spaceId, itemId, purchased)
        purchaseEntry = pantryEntry
    }

    override suspend fun applyCooked(
        spaceId: String,
        crossOff: List<ShoppingCrossOff>,
        finishedPantryIds: List<String>,
        restock: List<ShoppingWrite>,
    ) = Unit

    var deleteGate: CompletableDeferred<Unit>? = null
    var restoreGate: CompletableDeferred<Unit>? = null

    override suspend fun deleteItem(spaceId: String, itemId: String) {
        deleteGate?.await()
        throwNextFailure()
        deleted += itemId
    }

    override suspend fun restoreItem(spaceId: String, item: ShoppingItem) {
        restoreGate?.await()
        throwNextFailure()
        restoredSpaceId = spaceId
        restored += item
    }

    override suspend fun clearPurchased(spaceId: String): Int {
        throwNextFailure()
        return clearCount
    }

    private fun throwNextFailure() {
        nextFailure?.let { throw it }
    }
}

internal fun shoppingItem() = ShoppingItem(
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

internal data class RelistCall(
    val itemId: String,
    val name: String,
    val quantity: String?,
    val notes: String?,
    val supermarket: Supermarket?,
    val brand: String?,
)

private fun catalogIngredient() = CatalogIngredient(id = "leche", name = "Leche")
