package com.dlunaunizar.bobitos.feature.recipes

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.recipeimport.ImportFailure
import com.dlunaunizar.bobitos.data.recipeimport.ImportedRecipe
import com.dlunaunizar.bobitos.data.recipeimport.RecipeImportException
import com.dlunaunizar.bobitos.data.recipeimport.RecipeImporter
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.RecipeRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
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
class RecipesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRecipeRepository()
    private val importer = FakeRecipeImporter()
    private val shoppingRepository = RecipesFakeShoppingRepository()
    private val prefsRepository = RecipesFakePrefsRepository()
    private val viewModel = RecipesViewModel(repository, importer, shoppingRepository, prefsRepository)

    @Test
    fun `observes global and personal recipes`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.globalState.value = listOf(recipe("g1", RecipeVisibility.GLOBAL, "Paella"))
        repository.mineState.value = listOf(recipe("m1", RecipeVisibility.PRIVATE, "Tortilla"))

        viewModel.observe()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("Paella"), (state.global as UiState.Content).value.map(Recipe::title))
        assertEquals(listOf("Tortilla"), (state.mine as UiState.Content).value.map(Recipe::title))
    }

    @Test
    fun `setQuery updates the state`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.setQuery("lente")

        assertEquals("lente", viewModel.uiState.value.query)
    }

    @Test
    fun `creating a recipe trims the title and reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.createRecipe(RecipeVisibility.PRIVATE, "  Tortilla  ", "  ", null)
        advanceUntilIdle()

        assertEquals(1, repository.createCount)
        assertEquals(RecipeVisibility.PRIVATE, repository.lastVisibility)
        assertEquals("Tortilla", repository.lastTitle)
        assertEquals(null, repository.lastSourceRecipeId)
        assertEquals(RecipeUiMessage.RecipeSaved, viewModel.uiState.value.notice)
    }

    @Test
    fun `creating a recipe forwards its ingredients`() = runTest(mainDispatcherRule.testDispatcher) {
        val ingredients = listOf(Ingredient("Huevo", "2", "uds"), Ingredient("Sal"))
        viewModel.createRecipe(RecipeVisibility.PRIVATE, "Tortilla", null, null, ingredients)
        advanceUntilIdle()

        assertEquals(ingredients, repository.lastIngredients)
    }

    @Test
    fun `an invalid recipe never reaches the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.createRecipe(RecipeVisibility.PRIVATE, "   ", null, null)

        assertEquals(0, repository.createCount)
        assertEquals(RecipeUiMessage.TitleRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `an admin can publish a global recipe`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.admin = true
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createRecipe(RecipeVisibility.GLOBAL, "Paella", null, null)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isAdmin)
        assertEquals(RecipeVisibility.GLOBAL, repository.lastVisibility)
    }

    @Test
    fun `a non-admin global recipe is downgraded to private`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createRecipe(RecipeVisibility.GLOBAL, "Paella", null, null)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isAdmin)
        assertEquals(RecipeVisibility.PRIVATE, repository.lastVisibility)
    }

    @Test
    fun `fork copies the source recipe including its ingredients and marks its origin`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val ingredients = listOf(Ingredient("Arroz", "300", "g"), Ingredient("Azafrán"))
            viewModel.fork(recipe("origin", RecipeVisibility.GLOBAL, "Paella común", ingredients))
            advanceUntilIdle()

            assertEquals(1, repository.createCount)
            assertEquals(RecipeVisibility.PRIVATE, repository.lastVisibility)
            assertEquals("Paella común", repository.lastTitle)
            assertEquals("origin", repository.lastSourceRecipeId)
            assertEquals(ingredients, repository.lastIngredients)
            assertEquals(RecipeUiMessage.RecipeForked, viewModel.uiState.value.notice)
        }

    @Test
    fun `a successful import leaves a draft to review`() = runTest(mainDispatcherRule.testDispatcher) {
        importer.result = ImportedRecipe(
            title = "Tortilla",
            description = "Batir y cuajar",
            category = "Cena",
            ingredients = listOf(Ingredient("Huevo", "2", null)),
            sourceUrl = "https://example.com/tortilla",
        )

        viewModel.importFromUrl("https://example.com/tortilla")
        advanceUntilIdle()

        val draft = viewModel.uiState.value.importDraft
        assertEquals("Tortilla", draft?.title)
        assertEquals("https://example.com/tortilla", draft?.sourceUrl)
        assertEquals(false, viewModel.uiState.value.isImporting)
    }

    @Test
    fun `consuming the draft clears it`() = runTest(mainDispatcherRule.testDispatcher) {
        importer.result = ImportedRecipe("Tortilla", null, null, emptyList(), "https://example.com/t")
        viewModel.importFromUrl("https://example.com/t")
        advanceUntilIdle()

        viewModel.consumeImportDraft()

        assertEquals(null, viewModel.uiState.value.importDraft)
    }

    @Test
    fun `a failed import surfaces an error and no draft`() = runTest(mainDispatcherRule.testDispatcher) {
        importer.failure = ImportFailure.NoRecipeFound

        viewModel.importFromUrl("https://example.com/not-a-recipe")
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.importDraft)
        assertEquals(RecipeUiMessage.ImportNoRecipe, viewModel.uiState.value.error)
        assertEquals(false, viewModel.uiState.value.isImporting)
    }

    @Test
    fun `the imported source url is forwarded on save`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.createRecipe(
            RecipeVisibility.PRIVATE,
            "Tortilla",
            null,
            null,
            emptyList(),
            sourceUrl = "https://example.com/tortilla",
        )
        advanceUntilIdle()

        assertEquals("https://example.com/tortilla", repository.lastSourceUrl)
    }

    @Test
    fun `adding a recipe to shopping opens a review of its ingredients`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()
        val recipe = recipe("r1", RecipeVisibility.PRIVATE, "Tortilla", listOf(Ingredient("Huevo", "4", "uds")))

        viewModel.addToShopping("home", recipe)
        advanceUntilIdle()

        assertEquals(listOf("Huevo"), viewModel.uiState.value.ingredientReview?.map { it.name })
    }

    @Test
    fun `confirming the review writes to shopping applying prefs`() = runTest(mainDispatcherRule.testDispatcher) {
        prefsRepository.prefsState.value = mapOf("huevo" to IngredientPref(Supermarket.DIA, "Marca"))
        viewModel.observe()
        advanceUntilIdle()
        viewModel.addToShopping(
            "home",
            recipe("r1", RecipeVisibility.PRIVATE, "Tortilla", listOf(Ingredient("Huevo", "4", "uds"))),
        )
        advanceUntilIdle()

        viewModel.confirmShoppingReview("home", listOf("4"))
        advanceUntilIdle()

        assertEquals(listOf("Huevo"), shoppingRepository.addedNames)
        assertEquals(listOf(Supermarket.DIA), shoppingRepository.addedSupermarkets)
        assertEquals(RecipeUiMessage.AddedToShopping, viewModel.uiState.value.notice)
        assertEquals(null, viewModel.uiState.value.ingredientReview)
    }

    @Test
    fun `adding a recipe without ingredients shows a message`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()

        viewModel.addToShopping("home", recipe("r1", RecipeVisibility.PRIVATE, "Agua", null))
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.ingredientReview)
        assertEquals(RecipeUiMessage.NoIngredients, viewModel.uiState.value.error)
    }
}

private class RecipesFakeShoppingRepository : ShoppingRepository {
    val addedNames = mutableListOf<String>()
    val addedSupermarkets = mutableListOf<Supermarket?>()

    override fun items(spaceId: String): Flow<List<ShoppingItem>> = MutableStateFlow(emptyList())
    override suspend fun addItem(
        spaceId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) {
        addedNames += name
        addedSupermarkets += supermarket
    }

    override suspend fun updateItem(
        spaceId: String,
        itemId: String,
        name: String,
        quantity: String?,
        notes: String?,
        supermarket: Supermarket?,
        brand: String?,
    ) = Unit

    override suspend fun setPurchased(spaceId: String, itemId: String, purchased: Boolean) = Unit
    override suspend fun deleteItem(spaceId: String, itemId: String) = Unit
    override suspend fun clearPurchased(spaceId: String): Int = 0
}

private class RecipesFakePrefsRepository : IngredientPrefsRepository {
    val prefsState = MutableStateFlow<Map<String, IngredientPref>>(emptyMap())
    override fun prefs(): Flow<Map<String, IngredientPref>> = prefsState
    override suspend fun setPref(ingredientId: String, supermarket: Supermarket?, brand: String?) = Unit
    override suspend fun clearPref(ingredientId: String) = Unit
}

private class FakeRecipeRepository : RecipeRepository {
    val globalState = MutableStateFlow<List<Recipe>>(emptyList())
    val mineState = MutableStateFlow<List<Recipe>>(emptyList())
    var createCount = 0
    var lastVisibility: RecipeVisibility? = null
    var lastTitle: String? = null
    var lastSourceRecipeId: String? = null
    var lastSourceUrl: String? = null
    var lastIngredients: List<Ingredient> = emptyList()
    var admin = false

    override fun globalRecipes(): Flow<List<Recipe>> = globalState
    override fun myRecipes(): Flow<List<Recipe>> = mineState
    override fun isCurrentUserRecipeAdmin(): Boolean = admin
    override suspend fun createRecipe(
        visibility: RecipeVisibility,
        title: String,
        description: String?,
        category: String?,
        sourceRecipeId: String?,
        ingredients: List<Ingredient>,
        sourceUrl: String?,
    ) {
        createCount++
        lastVisibility = visibility
        lastTitle = title
        lastSourceRecipeId = sourceRecipeId
        lastSourceUrl = sourceUrl
        lastIngredients = ingredients
    }

    override suspend fun updateRecipe(
        recipeId: String,
        title: String,
        description: String?,
        category: String?,
        ingredients: List<Ingredient>,
    ) = Unit

    override suspend fun deleteRecipe(recipeId: String) = Unit
}

private class FakeRecipeImporter : RecipeImporter {
    var result: ImportedRecipe? = null
    var failure: ImportFailure? = null

    override suspend fun import(url: String): ImportedRecipe {
        failure?.let { throw RecipeImportException(it) }
        return result ?: error("No import result configured")
    }
}

private fun recipe(id: String, visibility: RecipeVisibility, title: String, ingredients: List<Ingredient>? = null) =
    Recipe(
        id = id,
        ownerUid = "u",
        visibility = visibility,
        title = title,
        description = null,
        category = null,
        ingredients = ingredients,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
    )
