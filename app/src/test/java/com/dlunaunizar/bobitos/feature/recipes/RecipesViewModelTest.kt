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
}

private class FakeRecipeRepository : RecipeRepository {
    val globalState = MutableStateFlow<List<Recipe>>(emptyList())
    val mineState = MutableStateFlow<List<Recipe>>(emptyList())

    override fun globalRecipes(): Flow<List<Recipe>> = globalState
    override fun myRecipes(): Flow<List<Recipe>> = mineState
    override suspend fun createRecipe(
        visibility: RecipeVisibility,
        title: String,
        description: String?,
        category: String?,
    ) {
        error("no usado en el test")
    }

    override suspend fun updateRecipe(recipeId: String, title: String, description: String?, category: String?) {
        error("no usado en el test")
    }

    override suspend fun deleteRecipe(recipeId: String) {
        error("no usado en el test")
    }
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
