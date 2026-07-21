package com.dlunaunizar.bobitos.feature.recipes

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.data.repository.RecipeRepository
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
    private val viewModel = RecipesViewModel(repository)

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
    fun `fork copies the source recipe and marks its origin`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.fork(recipe("origin", RecipeVisibility.GLOBAL, "Paella común"))
        advanceUntilIdle()

        assertEquals(1, repository.createCount)
        assertEquals(RecipeVisibility.PRIVATE, repository.lastVisibility)
        assertEquals("Paella común", repository.lastTitle)
        assertEquals("origin", repository.lastSourceRecipeId)
        assertEquals(RecipeUiMessage.RecipeForked, viewModel.uiState.value.notice)
    }
}

private class FakeRecipeRepository : RecipeRepository {
    val globalState = MutableStateFlow<List<Recipe>>(emptyList())
    val mineState = MutableStateFlow<List<Recipe>>(emptyList())
    var createCount = 0
    var lastVisibility: RecipeVisibility? = null
    var lastTitle: String? = null
    var lastSourceRecipeId: String? = null
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
    ) {
        createCount++
        lastVisibility = visibility
        lastTitle = title
        lastSourceRecipeId = sourceRecipeId
    }

    override suspend fun updateRecipe(recipeId: String, title: String, description: String?, category: String?) = Unit

    override suspend fun deleteRecipe(recipeId: String) = Unit
}

private fun recipe(id: String, visibility: RecipeVisibility, title: String) = Recipe(
    id = id,
    ownerUid = "u",
    visibility = visibility,
    title = title,
    description = null,
    category = null,
    createdBy = "u",
    createdByName = "U",
    createdAt = Instant.EPOCH,
    updatedBy = "u",
    updatedAt = Instant.EPOCH,
)
