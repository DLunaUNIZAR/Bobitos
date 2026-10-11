package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Nutrition
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.openfoodfacts.OffException
import com.dlunaunizar.bobitos.data.openfoodfacts.OffFailure
import com.dlunaunizar.bobitos.data.openfoodfacts.OffProduct
import com.dlunaunizar.bobitos.data.openfoodfacts.OpenFoodFactsClient
import com.dlunaunizar.bobitos.data.repository.BrandFailure
import com.dlunaunizar.bobitos.data.repository.BrandInput
import com.dlunaunizar.bobitos.data.repository.BrandRepositoryException
import com.dlunaunizar.bobitos.data.repository.IngredientBrandRepository
import com.dlunaunizar.bobitos.data.repository.IngredientPrefFailure
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsException
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
import com.dlunaunizar.bobitos.data.repository.PersonalIngredientRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class IngredientDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = DetailFakeIngredientRepo()
    private val personalRepository = DetailFakePersonalRepo()
    private val prefsRepository = DetailFakePrefsRepo()
    private val brandRepository = DetailFakeBrandRepo()
    private val offClient = DetailFakeOffClient()
    private val viewModel =
        IngredientDetailViewModel(repository, personalRepository, prefsRepository, brandRepository, offClient)

    @Test
    fun `observe resolves a common ingredient before a personal one with the same id`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.catalogState.value = listOf(detailIngredient("tomate", "Tomate común"))
            personalRepository.state.value = listOf(detailIngredient("tomate", "Tomate mío", personal = true))

            viewModel.observe("tomate")
            advanceUntilIdle()

            assertEquals("Tomate común", viewModel.uiState.value.ingredient?.name)

            repository.catalogState.value = emptyList()
            advanceUntilIdle()
            assertEquals("Tomate mío", viewModel.uiState.value.ingredient?.name)
        }

    @Test
    fun `observe shows only this ingredient's brands, favorites first`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.catalogState.value = listOf(detailIngredient("tomate", "Tomate"), detailIngredient("sal", "Sal"))
        brandRepository.brandsState.value = listOf(
            detailBrand("b1", "tomate", "Hacendado"),
            detailBrand("b2", "sal", "Ajena"),
            detailBrand("b3", "tomate", "Zeta").copy(favorite = true),
        )
        prefsRepository.state.value = mapOf("tomate" to IngredientPref(listOf(Supermarket.DIA)))

        viewModel.observe("tomate")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("Zeta", "Hacendado"), state.brands.map(IngredientBrand::name))
        assertEquals(listOf(Supermarket.DIA), state.stores)
    }

    @Test
    fun `canEditIngredient is true for personal and for admins on common`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.catalogState.value = listOf(detailIngredient("tomate", "Tomate"))
            personalRepository.state.value = listOf(detailIngredient("mio", "Mío", personal = true))

            viewModel.observe("tomate")
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.canEditIngredient)
            viewModel.stopObserving()

            viewModel.observe("mio")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.canEditIngredient)
            viewModel.stopObserving()

            repository.admin = true
            viewModel.observe("tomate")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.canEditIngredient)
        }

    @Test
    fun `toggling a store saves the new ordered list and is not an editor action`() =
        runTest(mainDispatcherRule.testDispatcher) {
            prefsRepository.state.value = mapOf("tomate" to IngredientPref(listOf(Supermarket.DIA)))
            viewModel.observe("tomate")
            advanceUntilIdle()

            viewModel.toggleStore(Supermarket.MERCADONA)
            advanceUntilIdle()

            assertEquals("tomate" to listOf(Supermarket.DIA, Supermarket.MERCADONA), prefsRepository.lastSet)
            assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)

            viewModel.toggleStore(Supermarket.DIA)
            advanceUntilIdle()
            assertEquals("tomate" to listOf(Supermarket.MERCADONA), prefsRepository.lastSet)
        }

    @Test
    fun `two quick store toggles with the first write pending both reach the repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            prefsRepository.gate = CompletableDeferred()
            viewModel.observe("tomate")
            advanceUntilIdle()

            viewModel.toggleStore(Supermarket.MERCADONA)
            viewModel.toggleStore(Supermarket.DIA)
            advanceUntilIdle()

            assertEquals(
                listOf(listOf(Supermarket.MERCADONA), listOf(Supermarket.MERCADONA, Supermarket.DIA)),
                prefsRepository.calls,
            )
            assertEquals(listOf(Supermarket.MERCADONA, Supermarket.DIA), viewModel.uiState.value.stores)
            assertFalse(viewModel.uiState.value.isSaving)
        }

    @Test
    fun `a pending store toggle leaves editorSave alone and does not block a brand editor save`() =
        runTest(mainDispatcherRule.testDispatcher) {
            prefsRepository.gate = CompletableDeferred()
            viewModel.observe("tomate")
            advanceUntilIdle()

            viewModel.toggleStore(Supermarket.MERCADONA)
            advanceUntilIdle()
            assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)

            viewModel.saveBrand("b1", BrandDraft.of("Otra", "", null))
            advanceUntilIdle()

            assertEquals("b1", brandRepository.lastUpdateId)
            assertEquals(IngredientUiMessage.BrandSaved, viewModel.uiState.value.notice)
        }

    @Test
    fun `setting favorite with a pending write updates the brand at once without isSaving`() =
        runTest(mainDispatcherRule.testDispatcher) {
            brandRepository.favoriteGate = CompletableDeferred()
            brandRepository.brandsState.value = listOf(
                detailBrand("b1", "tomate", "Alfa"),
                detailBrand("b2", "tomate", "Beta"),
            )
            viewModel.observe("tomate")
            advanceUntilIdle()

            viewModel.setFavorite("b2", true)
            advanceUntilIdle()

            val brands = viewModel.uiState.value.brands
            assertEquals(listOf("b2", "b1"), brands.map { it.id })
            assertTrue(brands.first().favorite)
            assertFalse(viewModel.uiState.value.isSaving)
        }

    @Test
    fun `a failing store write shows the error`() = runTest(mainDispatcherRule.testDispatcher) {
        prefsRepository.error = IngredientPrefsException(IngredientPrefFailure.Network)
        viewModel.observe("tomate")
        advanceUntilIdle()

        viewModel.toggleStore(Supermarket.MERCADONA)
        advanceUntilIdle()

        assertEquals(IngredientUiMessage.NetworkError, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `setting favorite forwards it`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("tomate")
        advanceUntilIdle()

        viewModel.setFavorite("b1", true)
        advanceUntilIdle()

        assertEquals("b1" to true, brandRepository.lastFavorite)
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
    }

    @Test
    fun `saving a new brand sends favorite and stores`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("tomate")
        advanceUntilIdle()
        val draft = BrandDraft.of("Hacendado", "8410000000000", null)
            .copy(favorite = true, stores = listOf(Supermarket.ALCAMPO, Supermarket.DIA))
            .withNutrition(0, "30")

        viewModel.saveBrand(null, draft)
        advanceUntilIdle()

        val input = brandRepository.lastAdd
        assertEquals("tomate", input?.ingredientId)
        assertEquals("Hacendado", input?.name)
        assertEquals("8410000000000", input?.barcode)
        assertEquals(Nutrition(energyKcal = 30.0), input?.nutrition)
        assertEquals(true, input?.favorite)
        assertEquals(listOf(Supermarket.ALCAMPO, Supermarket.DIA), input?.stores)
        assertEquals(IngredientUiMessage.BrandSaved, viewModel.uiState.value.notice)
    }

    @Test
    fun `editing a brand never changes its ingredient`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("tomate")
        advanceUntilIdle()

        viewModel.saveBrand("b1", BrandDraft.of("Otra", "", null).copy(stores = listOf(Supermarket.DIA)))
        advanceUntilIdle()

        assertEquals("b1", brandRepository.lastUpdateId)
        assertEquals("tomate", brandRepository.lastUpdate?.ingredientId)
        assertEquals("Otra", brandRepository.lastUpdate?.name)
        assertEquals(listOf(Supermarket.DIA), brandRepository.lastUpdate?.stores)
    }

    @Test
    fun `a blank brand name never reaches the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("tomate")
        advanceUntilIdle()

        viewModel.saveBrand(null, BrandDraft.of("  ", "", null))

        assertEquals(0, brandRepository.addCount)
        assertEquals(IngredientUiMessage.BrandNameRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `a repository validation error is shown`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("tomate")
        advanceUntilIdle()
        brandRepository.addError = BrandRepositoryException(BrandFailure.NameTooLong)

        viewModel.saveBrand(null, BrandDraft.of("x".repeat(61), "", null))
        advanceUntilIdle()

        assertEquals(IngredientUiMessage.BrandNameTooLong, viewModel.uiState.value.error)
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
    }

    @Test
    fun `deleting a personal ingredient uses the personal repository and finishes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            personalRepository.state.value = listOf(detailIngredient("mio", "Mío", personal = true))
            viewModel.observe("mio")
            advanceUntilIdle()

            viewModel.deleteIngredient()
            advanceUntilIdle()

            assertEquals("mio", personalRepository.deletedId)
            assertNull(repository.deletedId)
            assertTrue(viewModel.uiState.value.finished)
        }

    @Test
    fun `deleting a common ingredient as admin uses the catalog repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.admin = true
            repository.catalogState.value = listOf(detailIngredient("tomate", "Tomate"))
            viewModel.observe("tomate")
            advanceUntilIdle()

            viewModel.deleteIngredient()
            advanceUntilIdle()

            assertEquals("tomate", repository.deletedId)
            assertNull(personalRepository.deletedId)
            assertTrue(viewModel.uiState.value.finished)
        }

    @Test
    fun `updating a personal ingredient uses the personal repository`() = runTest(mainDispatcherRule.testDispatcher) {
        personalRepository.state.value = listOf(detailIngredient("mio", "Mío", personal = true))
        viewModel.observe("mio")
        advanceUntilIdle()

        viewModel.updateIngredient("Mío 2", "Verdura", "kg")
        advanceUntilIdle()

        assertEquals("mio", personalRepository.updatedId)
        assertNull(repository.updatedId)
    }

    @Test
    fun `scanning a known barcode leaves a prefilled brand draft`() = runTest(mainDispatcherRule.testDispatcher) {
        offClient.result = OffProduct("Tomate frito", "Hacendado", Nutrition(energyKcal = 80.0))
        viewModel.observe("tomate")
        advanceUntilIdle()

        viewModel.lookupBarcode("8410000000000")
        advanceUntilIdle()

        val draft = viewModel.uiState.value.scannedBrand
        assertEquals("Hacendado", draft?.name)
        assertEquals("8410000000000", draft?.barcode)
        assertEquals(Nutrition(energyKcal = 80.0), draft?.nutrition)
    }

    @Test
    fun `scanning an unknown barcode still opens a draft with a notice`() = runTest(mainDispatcherRule.testDispatcher) {
        offClient.result = null
        viewModel.observe("tomate")
        advanceUntilIdle()

        viewModel.lookupBarcode("0000000000000")
        advanceUntilIdle()

        assertEquals("0000000000000", viewModel.uiState.value.scannedBrand?.barcode)
        assertEquals(IngredientUiMessage.ScanNotFound, viewModel.uiState.value.notice)
    }

    @Test
    fun `a network error while scanning surfaces an error`() = runTest(mainDispatcherRule.testDispatcher) {
        offClient.failure = OffException(OffFailure.Network)
        viewModel.observe("tomate")
        advanceUntilIdle()

        viewModel.lookupBarcode("8410000000000")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.scannedBrand)
        assertEquals(IngredientUiMessage.ScanFailed, viewModel.uiState.value.error)
    }

    @Test
    fun `scanning a barcode of this ingredient's brand opens its editor`() =
        runTest(mainDispatcherRule.testDispatcher) {
            brandRepository.brandsState.value = listOf(detailBrand("b1", "tomate", "Hacendado").copy(barcode = "841"))
            viewModel.observe("tomate")
            advanceUntilIdle()

            viewModel.lookupBarcode("841")
            advanceUntilIdle()

            assertEquals("b1", viewModel.uiState.value.openBrandId)
            assertNull(viewModel.uiState.value.scannedBrand)
            assertEquals(0, offClient.calls)

            viewModel.consumeOpenBrand()
            assertNull(viewModel.uiState.value.openBrandId)
        }

    @Test
    fun `scanning a barcode of another ingredient's brand shows a notice`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.catalogState.value = listOf(detailIngredient("tomate", "Tomate"), detailIngredient("sal", "Sal"))
            brandRepository.brandsState.value = listOf(detailBrand("b2", "sal", "Ajena").copy(barcode = "842"))
            viewModel.observe("tomate")
            advanceUntilIdle()

            viewModel.lookupBarcode("842")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(IngredientUiMessage.BarcodeInOtherIngredient, state.notice)
            assertEquals(BarcodeConflict(brandName = "Ajena", ingredientName = "Sal"), state.barcodeConflict)
            assertNull(state.openBrandId)
            assertNull(state.scannedBrand)
            assertEquals(0, offClient.calls)
        }

    @Test
    fun `parseNutritionValue accepts comma and dot and rejects blanks`() {
        assertEquals(1.5, parseNutritionValue("1,5"))
        assertEquals(30.0, parseNutritionValue("30"))
        assertNull(parseNutritionValue("  "))
        assertNull(parseNutritionValue("abc"))
    }

    @Test
    fun `ficha save is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.catalogState.value = listOf(detailIngredient("tomate", "Tomate"))
        viewModel.observe("tomate")
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        repository.updateGate = gate

        viewModel.updateIngredient("Tomate", null, null)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
        viewModel.consumeEditorSave()
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
    }

    @Test
    fun `brand save is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("tomate")
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        brandRepository.addGate = gate

        viewModel.saveBrand(null, BrandDraft.of("Hacendado", "", null))
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
        viewModel.consumeEditorSave()

        val updateGate = CompletableDeferred<Unit>()
        brandRepository.updateGate = updateGate
        viewModel.saveBrand("b1", BrandDraft.of("Otra", "", null))
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        updateGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
    }

    @Test
    fun `ficha and brand saves with timeout or validation leave FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.catalogState.value = listOf(detailIngredient("tomate", "Tomate"))
        viewModel.observe("tomate")
        advanceUntilIdle()
        viewModel.saveBrand(null, BrandDraft.of("  ", "", null))
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        viewModel.consumeEditorSave()

        repository.hang = true
        viewModel.updateIngredient("Tomate", null, null)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(IngredientUiMessage.SaveTimeout, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `delete never touches editorSave`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.admin = true
        repository.catalogState.value = listOf(detailIngredient("tomate", "Tomate"))
        viewModel.observe("tomate")
        advanceUntilIdle()

        viewModel.deleteBrand("b1")
        advanceUntilIdle()
        viewModel.deleteIngredient()
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
        assertTrue(viewModel.uiState.value.finished)
    }
}

private fun detailIngredient(id: String, name: String, personal: Boolean = false) =
    CatalogIngredient(id = id, name = name, personal = personal)

private fun detailBrand(id: String, ingredientId: String, name: String) =
    IngredientBrand(id = id, ingredientId = ingredientId, name = name)

private class DetailFakeIngredientRepo : IngredientRepository {
    val catalogState = MutableStateFlow<List<CatalogIngredient>>(emptyList())
    var admin = false
    var uid: String? = null
    var updateGate: CompletableDeferred<Unit>? = null
    var deletedId: String? = null
    var updatedId: String? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    override fun catalog(): Flow<List<CatalogIngredient>> = catalogState
    override fun isCurrentUserCatalogAdmin(): Boolean = admin
    override fun currentUserId(): String? = uid
    override suspend fun createIngredient(name: String, category: String?, defaultUnit: String?) = Unit
    override suspend fun updateIngredient(id: String, name: String, category: String?, defaultUnit: String?) {
        updatedId = id
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                error("cancelled")
            }
        }
        updateGate?.await()
    }

    override suspend fun deleteIngredient(id: String) {
        deletedId = id
    }
}

private class DetailFakePersonalRepo : PersonalIngredientRepository {
    val state = MutableStateFlow<List<CatalogIngredient>>(emptyList())
    var deletedId: String? = null
    var updatedId: String? = null

    override fun ingredients(): Flow<List<CatalogIngredient>> = state
    override suspend fun create(name: String, category: String?, defaultUnit: String?): String = "new"
    override suspend fun update(id: String, name: String, category: String?, defaultUnit: String?) {
        updatedId = id
    }

    override suspend fun delete(id: String) {
        deletedId = id
    }
}

private class DetailFakePrefsRepo : IngredientPrefsRepository {
    val state = MutableStateFlow<Map<String, IngredientPref>>(emptyMap())
    var lastSet: Pair<String, List<Supermarket>>? = null

    override fun prefs(): Flow<Map<String, IngredientPref>> = state
    var gate: CompletableDeferred<Unit>? = null
    var error: Throwable? = null
    val calls = mutableListOf<List<Supermarket>>()

    override suspend fun setStores(ingredientId: String, stores: List<Supermarket>) {
        calls += stores
        gate?.await()
        error?.let { throw it }
        lastSet = ingredientId to stores
        state.value = state.value + (ingredientId to IngredientPref(stores))
    }
}

private class DetailFakeBrandRepo : IngredientBrandRepository {
    val brandsState = MutableStateFlow<List<IngredientBrand>>(emptyList())
    var addCount = 0
    var addGate: CompletableDeferred<Unit>? = null
    var updateGate: CompletableDeferred<Unit>? = null
    var addError: Throwable? = null
    var lastAdd: BrandInput? = null
    var lastUpdate: BrandInput? = null
    var lastUpdateId: String? = null
    var lastFavorite: Pair<String, Boolean>? = null

    override fun brands(): Flow<List<IngredientBrand>> = brandsState
    override suspend fun addBrand(input: BrandInput): String {
        addGate?.await()
        addError?.let { throw it }
        addCount++
        lastAdd = input
        return "new"
    }

    override suspend fun updateBrand(brandId: String, input: BrandInput) {
        updateGate?.await()
        lastUpdateId = brandId
        lastUpdate = input
    }

    var favoriteGate: CompletableDeferred<Unit>? = null

    override suspend fun setFavorite(brandId: String, favorite: Boolean) {
        favoriteGate?.await()
        lastFavorite = brandId to favorite
    }
    override suspend fun deleteBrand(brandId: String) = Unit
}

private class DetailFakeOffClient : OpenFoodFactsClient {
    var result: OffProduct? = null
    var failure: OffException? = null
    var calls = 0

    override suspend fun lookup(barcode: String): OffProduct? {
        calls++
        failure?.let { throw it }
        return result
    }
}
