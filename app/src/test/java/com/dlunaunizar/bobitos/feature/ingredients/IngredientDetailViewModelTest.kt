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
import com.dlunaunizar.bobitos.data.repository.IngredientBrandRepository
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class IngredientDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = DetailFakeIngredientRepo()
    private val prefsRepository = DetailFakePrefsRepo()
    private val brandRepository = DetailFakeBrandRepo()
    private val offClient = DetailFakeOffClient()
    private val viewModel = IngredientDetailViewModel(repository, prefsRepository, brandRepository, offClient)

    @Test
    fun `observe loads the ingredient, its brands and pref`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.catalogState.value = listOf(detailIngredient("tomate", "Tomate"), detailIngredient("sal", "Sal"))
        brandRepository.brandsState.value = listOf(detailBrand("b1", "tomate", "Hacendado"))
        prefsRepository.prefsState.value = mapOf("tomate" to IngredientPref(Supermarket.DIA, "Hacendado"))

        viewModel.observe("tomate")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Tomate", state.ingredient?.name)
        assertEquals(listOf("Hacendado"), state.brands.map(IngredientBrand::name))
        assertEquals(Supermarket.DIA, state.pref?.supermarket)
    }

    @Test
    fun `adding a brand forwards name and nutrition`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("tomate")
        advanceUntilIdle()

        viewModel.addBrand("Hacendado", "8410000000000", Nutrition(energyKcal = 30.0, salt = 0.1))
        advanceUntilIdle()

        assertEquals("tomate", brandRepository.lastIngredientId)
        assertEquals("Hacendado", brandRepository.lastName)
        assertEquals(Nutrition(energyKcal = 30.0, salt = 0.1), brandRepository.lastNutrition)
        assertEquals(IngredientUiMessage.BrandSaved, viewModel.uiState.value.notice)
    }

    @Test
    fun `a blank brand name never reaches the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("tomate")
        advanceUntilIdle()

        viewModel.addBrand("  ", null, null)

        assertEquals(0, brandRepository.addCount)
        assertEquals(IngredientUiMessage.BrandNameRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `deleting the ingredient marks the screen finished`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("tomate")
        advanceUntilIdle()

        viewModel.deleteIngredient()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.finished)
    }

    @Test
    fun `canEditBrand is true for own brands and admins`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.uid = "me"
        viewModel.observe("tomate")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(true, state.canEditBrand(detailBrand("b1", "tomate", "Mía", owner = "me")))
        assertEquals(false, state.canEditBrand(detailBrand("b2", "tomate", "Ajena", owner = "otro")))
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
    fun `parseNutritionValue accepts comma and dot and rejects blanks`() {
        assertEquals(1.5, parseNutritionValue("1,5"))
        assertEquals(30.0, parseNutritionValue("30"))
        assertNull(parseNutritionValue("  "))
        assertNull(parseNutritionValue("abc"))
    }

    @Test
    fun `ficha save is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
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

        viewModel.addBrand("Hacendado", null, null)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
        viewModel.consumeEditorSave()

        val updateGate = CompletableDeferred<Unit>()
        brandRepository.updateGate = updateGate
        viewModel.updateBrand("b1", "Otra", null, null)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        updateGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
    }

    @Test
    fun `ficha and brand saves with timeout or validation leave FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("tomate")
        advanceUntilIdle()
        viewModel.addBrand("  ", null, null)
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
    fun `delete and prefs never touch editorSave`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("tomate")
        advanceUntilIdle()

        viewModel.setPref(Supermarket.DIA, null)
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
        advanceUntilIdle()
        viewModel.deleteBrand("b1")
        advanceUntilIdle()
        viewModel.deleteIngredient()
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
        assertTrue(viewModel.uiState.value.finished)
    }
}

private fun detailIngredient(id: String, name: String, ownerUid: String = "owner") = CatalogIngredient(
    id = id,
    name = name,
    ownerUid = ownerUid,
    createdBy = ownerUid,
    createdByName = ownerUid,
    createdAt = Instant.EPOCH,
    updatedBy = ownerUid,
    updatedAt = Instant.EPOCH,
)

private fun detailBrand(id: String, ingredientId: String, name: String, owner: String = "owner") = IngredientBrand(
    id = id,
    ingredientId = ingredientId,
    name = name,
    ownerUid = owner,
    createdBy = owner,
    createdByName = owner,
    createdAt = Instant.EPOCH,
    updatedBy = owner,
    updatedAt = Instant.EPOCH,
)

private class DetailFakeIngredientRepo : IngredientRepository {
    val catalogState = MutableStateFlow<List<CatalogIngredient>>(emptyList())
    var admin = false
    var uid: String? = null
    var updateGate: CompletableDeferred<Unit>? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    override fun catalog(): Flow<List<CatalogIngredient>> = catalogState
    override fun isCurrentUserCatalogAdmin(): Boolean = admin
    override fun currentUserId(): String? = uid
    override suspend fun ingredientById(id: String): CatalogIngredient? = catalogState.value.firstOrNull { it.id == id }
    override suspend fun createIngredient(name: String, category: String?, defaultUnit: String?) = Unit
    override suspend fun updateIngredient(id: String, name: String, category: String?, defaultUnit: String?) {
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                error("cancelled")
            }
        }
        updateGate?.await()
    }

    override suspend fun deleteIngredient(id: String) = Unit
}

private class DetailFakePrefsRepo : IngredientPrefsRepository {
    val prefsState = MutableStateFlow<Map<String, IngredientPref>>(emptyMap())
    override fun prefs(): Flow<Map<String, IngredientPref>> = prefsState
    override suspend fun setPref(ingredientId: String, supermarket: Supermarket?, brand: String?) = Unit
    override suspend fun clearPref(ingredientId: String) = Unit
}

private class DetailFakeBrandRepo : IngredientBrandRepository {
    val brandsState = MutableStateFlow<List<IngredientBrand>>(emptyList())
    var addCount = 0
    var addGate: CompletableDeferred<Unit>? = null
    var updateGate: CompletableDeferred<Unit>? = null
    var lastIngredientId: String? = null
    var lastName: String? = null
    var lastNutrition: Nutrition? = null

    override fun brands(ingredientId: String): Flow<List<IngredientBrand>> = brandsState
    override suspend fun addBrand(ingredientId: String, name: String, barcode: String?, nutrition: Nutrition?) {
        addGate?.await()
        addCount++
        lastIngredientId = ingredientId
        lastName = name
        lastNutrition = nutrition
    }

    override suspend fun updateBrand(
        ingredientId: String,
        brandId: String,
        name: String,
        barcode: String?,
        nutrition: Nutrition?,
    ) {
        updateGate?.await()
    }

    override suspend fun deleteBrand(ingredientId: String, brandId: String) = Unit
}

private class DetailFakeOffClient : OpenFoodFactsClient {
    var result: OffProduct? = null
    var failure: OffException? = null

    override suspend fun lookup(barcode: String): OffProduct? {
        failure?.let { throw it }
        return result
    }
}
