package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class IngredientsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeIngredientRepository()
    private val prefsRepository = FakeIngredientPrefsRepository()
    private val viewModel = IngredientsViewModel(repository, prefsRepository)

    @Test
    fun `observes the catalog and prefs`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.catalogState.value = listOf(ingredient("tomate", "Tomate"))
        prefsRepository.prefsState.value = mapOf("tomate" to IngredientPref(Supermarket.DIA, "Marca"))

        viewModel.observe()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("Tomate"), (state.catalog as UiState.Content).value.map(CatalogIngredient::name))
        assertEquals(IngredientPref(Supermarket.DIA, "Marca"), state.prefs["tomate"])
    }

    @Test
    fun `creating a new ingredient forwards it`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createIngredient("  Cebolla  ", null, "ud")
        advanceUntilIdle()

        assertEquals(1, repository.createCount)
        assertEquals("Cebolla", repository.lastCreatedName)
        assertEquals(IngredientUiMessage.Saved, viewModel.uiState.value.notice)
    }

    @Test
    fun `a blank name never reaches the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.createIngredient("   ", null, null)

        assertEquals(0, repository.createCount)
        assertEquals(IngredientUiMessage.NameRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `a duplicate name is rejected before writing`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.catalogState.value = listOf(ingredient("tomate", "Tomate"))
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createIngredient("tomate", null, null)

        assertEquals(0, repository.createCount)
        assertEquals(IngredientUiMessage.AlreadyExists, viewModel.uiState.value.error)
    }

    @Test
    fun `setting a preference forwards it`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.setPref("tomate", Supermarket.MERCADONA, "Hacendado")
        advanceUntilIdle()

        assertEquals("tomate", prefsRepository.lastId)
        assertEquals(Supermarket.MERCADONA, prefsRepository.lastSupermarket)
        assertEquals("Hacendado", prefsRepository.lastBrand)
        assertEquals(IngredientUiMessage.PrefSaved, viewModel.uiState.value.notice)
    }

    @Test
    fun `canEdit is true for own ingredients and admins`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.uid = "me"
        viewModel.observe()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(true, state.canEdit(ingredient("a", "A", ownerUid = "me")))
        assertEquals(false, state.canEdit(ingredient("b", "B", ownerUid = "other")))
    }
}

private fun ingredient(id: String, name: String, ownerUid: String = "owner") = CatalogIngredient(
    id = id,
    name = name,
    ownerUid = ownerUid,
    createdBy = ownerUid,
    createdByName = ownerUid,
    createdAt = Instant.EPOCH,
    updatedBy = ownerUid,
    updatedAt = Instant.EPOCH,
)

private class FakeIngredientRepository : IngredientRepository {
    val catalogState = MutableStateFlow<List<CatalogIngredient>>(emptyList())
    var admin = false
    var uid: String? = null
    var createCount = 0
    var lastCreatedName: String? = null

    override fun catalog(): Flow<List<CatalogIngredient>> = catalogState
    override fun isCurrentUserCatalogAdmin(): Boolean = admin
    override fun currentUserId(): String? = uid
    override suspend fun createIngredient(name: String, category: String?, defaultUnit: String?) {
        createCount++
        lastCreatedName = name
    }

    override suspend fun updateIngredient(id: String, name: String, category: String?, defaultUnit: String?) = Unit
    override suspend fun deleteIngredient(id: String) = Unit
}

private class FakeIngredientPrefsRepository : IngredientPrefsRepository {
    val prefsState = MutableStateFlow<Map<String, IngredientPref>>(emptyMap())
    var lastId: String? = null
    var lastSupermarket: Supermarket? = null
    var lastBrand: String? = null

    override fun prefs(): Flow<Map<String, IngredientPref>> = prefsState
    override suspend fun setPref(ingredientId: String, supermarket: Supermarket?, brand: String?) {
        lastId = ingredientId
        lastSupermarket = supermarket
        lastBrand = brand
    }

    override suspend fun clearPref(ingredientId: String) {
        lastId = ingredientId
    }
}
