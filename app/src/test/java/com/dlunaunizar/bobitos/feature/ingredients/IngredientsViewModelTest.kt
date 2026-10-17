package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Nutrition
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.openfoodfacts.OffException
import com.dlunaunizar.bobitos.data.openfoodfacts.OffFailure
import com.dlunaunizar.bobitos.data.openfoodfacts.OffProduct
import com.dlunaunizar.bobitos.data.repository.DeletedIngredient
import com.dlunaunizar.bobitos.data.repository.IngredientFailure
import com.dlunaunizar.bobitos.data.repository.IngredientRepositoryException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class IngredientsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeIngredientRepository()
    private val personalRepository = FakePersonalIngredientRepository()
    private val prefsRepository = FakeIngredientPrefsRepository()
    private val brandRepository = FakeIngredientBrandRepository()
    private val offClient = FakeOffClient()
    private val relay = DeletedIngredientRelay()
    private val viewModel =
        IngredientsViewModel(repository, personalRepository, prefsRepository, brandRepository, offClient, relay)

    @Test
    fun `observe merges common and personal and carries brands and prefs`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.admin = true
            repository.catalogState.value = listOf(ingredient("tomate", "Tomate"))
            personalRepository.state.value = listOf(CatalogIngredient("canela", "Canela", personal = true))
            val brand = IngredientBrand(id = "b", ingredientId = "tomate", name = "Hida")
            brandRepository.brandsState.value = listOf(brand)
            prefsRepository.state.value = mapOf("tomate" to IngredientPref(listOf(Supermarket.DIA)))

            viewModel.observe()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(
                listOf("Canela", "Tomate"),
                (state.ingredients as UiState.Content).value.map(CatalogIngredient::name),
            )
            assertEquals(listOf(brand), state.brands)
            assertEquals(IngredientPref(listOf(Supermarket.DIA)), state.prefs["tomate"])
            assertTrue(state.isAdmin)
        }

    @Test
    fun `restoreIngredient brings back brands and stores`() = runTest(mainDispatcherRule.testDispatcher) {
        val deleted = DeletedIngredient(
            ingredient = CatalogIngredient("canela", "Canela", personal = true),
            brands = listOf(IngredientBrand(id = "b1", ingredientId = "canela", name = "Hida")),
            stores = listOf(Supermarket.DIA, Supermarket.MERCADONA),
            ownerUid = "me",
        )

        viewModel.restoreIngredient(deleted)
        advanceUntilIdle()

        assertEquals(deleted, personalRepository.restored)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a failed undo shows the error`() = runTest(mainDispatcherRule.testDispatcher) {
        personalRepository.restoreError = IngredientRepositoryException(IngredientFailure.AlreadyExists)

        val deleted =
            DeletedIngredient(CatalogIngredient("canela", "Canela", personal = true), emptyList(), emptyList(), "me")
        viewModel.restoreIngredient(deleted)
        advanceUntilIdle()

        assertEquals(IngredientUiMessage.AlreadyExists, viewModel.uiState.value.error)
    }

    @Test
    fun `a deleted ingredient posted to the relay is exposed and consumed once`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val canela = CatalogIngredient("canela", "Canela", personal = true)
            val deleted = DeletedIngredient(canela, emptyList(), emptyList(), "me")

            viewModel.observe()
            relay.post(deleted)
            assertEquals(deleted, viewModel.deleted.value)

            viewModel.consumeDeleted()
            assertNull(viewModel.deleted.value)
            assertNull(relay.deleted.value)
        }

    @Test
    fun `a deleted snapshot from another account is discarded`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.uid = "bea"
        val canela = CatalogIngredient("canela", "Canela", personal = true)
        val deleted = DeletedIngredient(canela, emptyList(), emptyList(), "ana")

        viewModel.observe()
        relay.post(deleted)
        advanceUntilIdle()
        assertNull(viewModel.deleted.value)
        assertNull(relay.deleted.value)

        viewModel.restoreIngredient(deleted)
        advanceUntilIdle()
        assertNull(personalRepository.restored)
    }

    @Test
    fun `a catalog error shows CatalogUnavailable and retry reloads it`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.failCatalog = true
        viewModel.observe()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.ingredients is UiState.Error)
        assertEquals(IngredientUiMessage.CatalogUnavailable, viewModel.uiState.value.catalogError)

        repository.failCatalog = false
        repository.catalogState.value = listOf(ingredient("tomate", "Tomate"))
        viewModel.retryCatalog()
        advanceUntilIdle()

        assertEquals(
            listOf("Tomate"),
            (viewModel.uiState.value.ingredients as UiState.Content).value.map(CatalogIngredient::name),
        )
        assertNull(viewModel.uiState.value.catalogError)
    }

    @Test
    fun `creating a new ingredient creates it as personal`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createIngredient("  Cebolla  ", null, "ud", common = false)
        advanceUntilIdle()

        assertEquals(1, personalRepository.createCount)
        assertEquals("Cebolla", personalRepository.lastCreatedName)
        assertEquals(0, repository.createCount)
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun creatingOneItemOffersUndoWithItsIdAndName() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createIngredient("  Cebolla  ", null, "ud", common = false)
        advanceUntilIdle()

        assertEquals(CreatedItem("cebolla", "Cebolla"), viewModel.uiState.value.created)
        assertNull(viewModel.uiState.value.notice)

        viewModel.consumeCreated()
        assertNull(viewModel.uiState.value.created)
    }

    @Test
    fun undoCreateDeletesExactlyThatItem() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        viewModel.undoCreate("cebolla")
        advanceUntilIdle()

        assertEquals("cebolla", personalRepository.deletedId)
        assertNull(repository.deletedId)
    }

    @Test
    fun editingDoesNotOfferUndo() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createIngredient("Tomate", null, null, common = false)
        advanceUntilIdle()
        viewModel.consumeCreated()
        // Un nombre que ya existe no escribe nada: abre la ficha existente y no ofrece deshacer.
        viewModel.createIngredient("Tomate", null, null, common = false)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.created)
    }

    @Test
    fun aCommonIngredientNeverOffersUndo() = runTest(mainDispatcherRule.testDispatcher) {
        repository.admin = true
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createIngredient("Cebolla", null, null, common = true)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.created)
        assertEquals(IngredientUiMessage.Saved, viewModel.uiState.value.notice)
    }

    @Test
    fun `an admin can create a common ingredient`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.admin = true
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createIngredient("Cebolla", null, null, common = true)
        advanceUntilIdle()

        assertEquals(1, repository.createCount)
        assertEquals(0, personalRepository.createCount)
    }

    @Test
    fun `a non-admin asking for common still creates a personal ingredient`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.admin = false
            viewModel.observe()
            advanceUntilIdle()

            viewModel.createIngredient("Cebolla", null, null, common = true)
            advanceUntilIdle()

            assertEquals(0, repository.createCount)
            assertEquals(1, personalRepository.createCount)
        }

    @Test
    fun `a name matching an existing ingredient by key opens it and does not write`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.catalogState.value = listOf(ingredient("limon", "Limón"))
            viewModel.observe()
            advanceUntilIdle()

            viewModel.createIngredient("limones", null, null, common = false)
            advanceUntilIdle()

            assertEquals(0, repository.createCount)
            assertEquals(0, personalRepository.createCount)
            val state = viewModel.uiState.value
            assertEquals("limon", state.openIngredientId)
            assertEquals(IngredientUiMessage.AlreadyExists, state.error)
            assertEquals("Limón", state.existingName)
            assertEquals(EditorSaveStatus.FAILED, state.editorSave)

            viewModel.consumeOpenIngredient()
            assertNull(viewModel.uiState.value.openIngredientId)
        }

    @Test
    fun `a blank name never reaches the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.createIngredient("   ", null, null, common = false)

        assertEquals(0, personalRepository.createCount)
        assertEquals(IngredientUiMessage.NameRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `scanning a barcode already in my brands opens it without calling Open Food Facts`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.catalogState.value = listOf(ingredient("tomate", "Tomate"))
            brandRepository.brandsState.value =
                listOf(IngredientBrand(id = "b1", ingredientId = "tomate", name = "Hida", barcode = "8410000000000"))
            viewModel.observe()
            advanceUntilIdle()

            viewModel.lookupBarcode("8410 0000 00000")
            advanceUntilIdle()

            assertEquals(OpenBrand("tomate", "b1"), viewModel.uiState.value.openBrand)
            assertEquals(0, offClient.calls)
            assertNull(viewModel.uiState.value.scanPick)

            viewModel.consumeOpenBrand()
            assertNull(viewModel.uiState.value.openBrand)
        }

    @Test
    fun `a barcode of an orphan brand is treated as new and goes to Open Food Facts`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.catalogState.value = listOf(ingredient("sal", "Sal"))
            brandRepository.brandsState.value =
                listOf(IngredientBrand(id = "b1", ingredientId = "borrado", name = "Hida", barcode = "8410000000000"))
            viewModel.observe()
            advanceUntilIdle()

            viewModel.lookupBarcode("8410000000000")
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.openBrand)
            assertEquals(1, offClient.calls)
            assertEquals("8410000000000", viewModel.uiState.value.scanPick?.barcode)
        }

    @Test
    fun `scanning a new barcode leaves a pick with the product data`() = runTest(mainDispatcherRule.testDispatcher) {
        offClient.result = OffProduct("Tomate frito", "Hacendado", Nutrition(energyKcal = 80.0))
        viewModel.observe()
        advanceUntilIdle()

        viewModel.lookupBarcode("8410000000000")
        advanceUntilIdle()

        assertEquals(
            ScanPick("8410000000000", "Tomate frito", "Hacendado", Nutrition(energyKcal = 80.0)),
            viewModel.uiState.value.scanPick,
        )
        assertNull(viewModel.uiState.value.scanBrandTarget)
    }

    @Test
    fun `a network error still leaves a pick with only the barcode`() = runTest(mainDispatcherRule.testDispatcher) {
        offClient.failure = OffException(OffFailure.Network)
        viewModel.observe()
        advanceUntilIdle()

        viewModel.lookupBarcode("8410000000000")
        advanceUntilIdle()

        assertEquals(ScanPick("8410000000000", "", "", null), viewModel.uiState.value.scanPick)
        assertEquals(IngredientUiMessage.ScanFailed, viewModel.uiState.value.error)
    }

    @Test
    fun `picking an ingredient and saving the brand adds it to that ingredient`() =
        runTest(mainDispatcherRule.testDispatcher) {
            offClient.result = OffProduct("Tomate frito", "Hacendado", Nutrition(energyKcal = 80.0))
            viewModel.observe()
            advanceUntilIdle()
            viewModel.lookupBarcode("8410000000000")
            advanceUntilIdle()

            viewModel.pickIngredientForScan("tomate")
            assertEquals("tomate", viewModel.uiState.value.scanBrandTarget)
            viewModel.saveScannedBrand(
                BrandDraft.of("Hacendado", "8410000000000", Nutrition(energyKcal = 80.0)).copy(favorite = true),
            )
            advanceUntilIdle()

            assertEquals("tomate", brandRepository.lastAddedIngredientId)
            assertEquals("Hacendado", brandRepository.lastAddedName)
            assertEquals("8410000000000", brandRepository.lastAddedBarcode)
            assertEquals(Nutrition(energyKcal = 80.0), brandRepository.lastAddedNutrition)
            assertTrue(brandRepository.lastAddedFavorite)
            val state = viewModel.uiState.value
            assertNull(state.scanPick)
            assertNull(state.scanBrandTarget)
            assertEquals(EditorSaveStatus.SAVED, state.editorSave)
            assertEquals(IngredientUiMessage.BrandSaved, state.notice)
        }

    @Test
    fun `creating a personal ingredient from a scan then saving adds the brand to it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            offClient.result = OffProduct("Tomate frito", "Hacendado", Nutrition())
            viewModel.observe()
            advanceUntilIdle()
            viewModel.lookupBarcode("841")
            advanceUntilIdle()

            viewModel.createPersonalForScan("Tomate frito", null, null)
            advanceUntilIdle()

            assertEquals(1, personalRepository.createCount)
            assertEquals(0, repository.createCount)
            assertEquals("tomate-frito", viewModel.uiState.value.scanBrandTarget)

            viewModel.saveScannedBrand(BrandDraft.of("Hacendado", "841", null))
            advanceUntilIdle()

            assertEquals("tomate-frito", brandRepository.lastAddedIngredientId)
            assertNull(viewModel.uiState.value.scanPick)
        }

    @Test
    fun `creating from a scan with a name that already exists uses the existing ingredient`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.catalogState.value = listOf(ingredient("tomate-frito", "Tomate frito"))
            viewModel.observe()
            advanceUntilIdle()
            viewModel.lookupBarcode("841")
            advanceUntilIdle()

            viewModel.createPersonalForScan("tomates fritos", null, null)
            advanceUntilIdle()

            assertEquals(0, personalRepository.createCount)
            assertEquals(0, repository.createCount)
            assertEquals("tomate-frito", viewModel.uiState.value.scanBrandTarget)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `cancelling the scan clears the pick`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()
        viewModel.lookupBarcode("841")
        advanceUntilIdle()
        viewModel.pickIngredientForScan("tomate")

        viewModel.cancelScan()

        assertNull(viewModel.uiState.value.scanPick)
        assertNull(viewModel.uiState.value.scanBrandTarget)
    }

    @Test
    fun `create is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        personalRepository.createGate = gate

        viewModel.createIngredient("Cebolla", null, null, common = false)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        assertTrue(viewModel.uiState.value.isSaving)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
        viewModel.consumeEditorSave()
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
    }

    @Test
    fun `editor validation errors and timeouts leave FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()
        viewModel.createIngredient("  ", null, null, common = false)
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        viewModel.consumeEditorSave()

        personalRepository.hang = true
        viewModel.createIngredient("Cebolla", null, null, common = false)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(IngredientUiMessage.SaveTimeout, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}

private fun ingredient(id: String, name: String) = CatalogIngredient(id = id, name = name)
