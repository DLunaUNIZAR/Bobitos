package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Nutrition
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.slug
import com.dlunaunizar.bobitos.data.openfoodfacts.OffProduct
import com.dlunaunizar.bobitos.data.openfoodfacts.OpenFoodFactsClient
import com.dlunaunizar.bobitos.data.repository.BrandInput
import com.dlunaunizar.bobitos.data.repository.IngredientBrandRepository
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
import com.dlunaunizar.bobitos.data.repository.PersonalIngredientRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class IngredientsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeIngredientRepository()
    private val personalRepository = FakePersonalIngredientRepository()
    private val prefsRepository = FakeIngredientPrefsRepository()
    private val brandRepository = FakeListBrandRepository()
    private val offClient = FakeListOffClient()
    private val viewModel =
        IngredientsViewModel(repository, personalRepository, prefsRepository, brandRepository, offClient)

    @Test
    fun `observe merges common and personal and carries brands and prefs`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.admin = true
            repository.catalogState.value = listOf(ingredient("tomate", "Tomate"))
            personalRepository.state.value = listOf(CatalogIngredient("canela", "Canela", personal = true))
            val brand = IngredientBrand(id = "b", ingredientId = "tomate", name = "Hida")
            brandRepository.brandsState.value = listOf(brand)
            prefsRepository.prefsState.value = mapOf("tomate" to IngredientPref(listOf(Supermarket.DIA)))

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
    fun `scanning from the list leaves a product draft`() = runTest(mainDispatcherRule.testDispatcher) {
        offClient.result = OffProduct("Tomate frito", "Hacendado", Nutrition(energyKcal = 80.0))
        viewModel.observe()
        advanceUntilIdle()

        viewModel.lookupBarcode("8410000000000")
        advanceUntilIdle()

        val product = viewModel.uiState.value.scannedProduct
        assertEquals("Tomate frito", product?.suggestedName)
        assertEquals("Hacendado", product?.brandName)
        assertEquals("8410000000000", product?.barcode)
    }

    @Test
    fun `creating from scan creates the ingredient and its brand`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createIngredientFromScan(
            "Tomate frito",
            null,
            null,
            "Hacendado",
            "8410000000000",
            Nutrition(energyKcal = 80.0),
        )
        advanceUntilIdle()

        assertEquals(1, repository.createCount)
        assertEquals("Tomate frito", repository.lastCreatedName)
        assertEquals("tomate-frito", brandRepository.lastIngredientId)
        assertEquals("Hacendado", brandRepository.lastName)
        assertEquals(IngredientUiMessage.Saved, viewModel.uiState.value.notice)
    }

    @Test
    fun `creating from scan for an existing ingredient only adds the brand`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.catalogState.value = listOf(ingredient("tomate-frito", "Tomate frito"))
            viewModel.observe()
            advanceUntilIdle()

            viewModel.createIngredientFromScan(
                "Tomate frito",
                null,
                null,
                "Hacendado",
                "841",
                Nutrition(energyKcal = 80.0),
            )
            advanceUntilIdle()

            assertEquals(0, repository.createCount)
            assertEquals("tomate-frito", brandRepository.lastIngredientId)
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

        val scanGate = CompletableDeferred<Unit>()
        repository.createGate = scanGate
        viewModel.createIngredientFromScan("Tomate frito", null, null, "Hacendado", "841", null)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        scanGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
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

private class FakeIngredientRepository : IngredientRepository {
    val catalogState = MutableStateFlow<List<CatalogIngredient>>(emptyList())
    var admin = false
    var uid: String? = null
    var createCount = 0
    var lastCreatedName: String? = null
    var createGate: CompletableDeferred<Unit>? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    var failCatalog = false

    override fun catalog(): Flow<List<CatalogIngredient>> = if (failCatalog) {
        flow { error("offline") }
    } else {
        catalogState
    }
    override fun isCurrentUserCatalogAdmin(): Boolean = admin
    override fun currentUserId(): String? = uid
    override suspend fun createIngredient(name: String, category: String?, defaultUnit: String?) {
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                error("cancelled")
            }
        }
        createGate?.await()
        createCount++
        lastCreatedName = name
    }

    override suspend fun updateIngredient(id: String, name: String, category: String?, defaultUnit: String?) = Unit
    override suspend fun deleteIngredient(id: String) = Unit
}

private class FakePersonalIngredientRepository : PersonalIngredientRepository {
    val state = MutableStateFlow<List<CatalogIngredient>>(emptyList())
    var createCount = 0
    var lastCreatedName: String? = null
    var createGate: CompletableDeferred<Unit>? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    override fun ingredients(): Flow<List<CatalogIngredient>> = state
    override suspend fun create(name: String, category: String?, defaultUnit: String?): String {
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                error("cancelled")
            }
        }
        createGate?.await()
        createCount++
        lastCreatedName = name
        return slug(name)
    }

    override suspend fun update(id: String, name: String, category: String?, defaultUnit: String?) = Unit
    override suspend fun delete(id: String) = Unit
}

private class FakeIngredientPrefsRepository : IngredientPrefsRepository {
    val prefsState = MutableStateFlow<Map<String, IngredientPref>>(emptyMap())

    override fun prefs(): Flow<Map<String, IngredientPref>> = prefsState
    override suspend fun setStores(ingredientId: String, stores: List<Supermarket>) = Unit
}

private class FakeListBrandRepository : IngredientBrandRepository {
    var lastIngredientId: String? = null
    var lastName: String? = null

    val brandsState = MutableStateFlow<List<IngredientBrand>>(emptyList())

    override fun brands(): Flow<List<IngredientBrand>> = brandsState
    override suspend fun addBrand(input: BrandInput): String {
        lastIngredientId = input.ingredientId
        lastName = input.name
        return "new"
    }

    override suspend fun updateBrand(brandId: String, input: BrandInput) = Unit
    override suspend fun setFavorite(brandId: String, favorite: Boolean) = Unit
    override suspend fun deleteBrand(brandId: String) = Unit
}

private class FakeListOffClient : OpenFoodFactsClient {
    var result: OffProduct? = null
    override suspend fun lookup(barcode: String): OffProduct? = result
}
