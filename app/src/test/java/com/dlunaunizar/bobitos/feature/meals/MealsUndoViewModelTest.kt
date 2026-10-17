package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.data.repository.MealFailure
import com.dlunaunizar.bobitos.data.repository.MealRepositoryException
import com.dlunaunizar.bobitos.feature.common.FakePantryRepository
import com.dlunaunizar.bobitos.feature.common.RecordingShoppingRepository
import com.dlunaunizar.bobitos.feature.common.ingredientShoppingSource
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientBrandRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientPrefsRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

// Borrar sin preguntar y «Deshacer» del ViewModel de Comidas (aparte de MealsViewModelTest, que ya es muy grande).
@OptIn(ExperimentalCoroutinesApi::class)
class MealsUndoViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mealRepository = FakeMealRepository()
    private val viewModel = MealsViewModel(
        mealRepository,
        FakeSpaceRepository(),
        FakeRecipeRepository(),
        RecordingShoppingRepository(),
        ingredientShoppingSource(
            FakeIngredientRepository(),
            FakeIngredientBrandRepository(),
            FakeIngredientPrefsRepository(),
        ),
        FakePantryRepository(),
    )

    @Test
    fun `undoing a delete keeps the id and the cooked state`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.restoreMeal("home", meal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas").copy(cooked = true))
        advanceUntilIdle()

        val (space, restored) = mealRepository.restored.single()
        assertEquals("home", space)
        assertEquals("m1", restored.id)
        assertTrue(restored.cooked)
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun `undo writes to the meal's own space`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("space-a")
        viewModel.observe("space-b")
        advanceUntilIdle()

        viewModel.restoreMeal("space-a", meal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas"))
        advanceUntilIdle()

        assertEquals("space-a", mealRepository.restored.single().first)
    }

    @Test
    fun `undo is offered only once the delete has finished`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        var offered = 0
        mealRepository.nextFailure = MealRepositoryException(MealFailure.Network)
        viewModel.deleteMeal("m1") { offered++ }
        advanceUntilIdle()
        assertEquals(0, offered)
        assertEquals(MealUiMessage.NetworkError, viewModel.uiState.value.error)

        mealRepository.nextFailure = null
        viewModel.deleteMeal("m1") { offered++ }
        advanceUntilIdle()
        assertEquals(1, offered)
        assertEquals(listOf("m1"), mealRepository.deleted)
    }

    @Test
    fun `a failed undo surfaces the error`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        mealRepository.nextFailure = MealRepositoryException(MealFailure.SpaceNotFound)

        viewModel.restoreMeal("home", meal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas"))
        advanceUntilIdle()

        assertEquals(MealUiMessage.SpaceNotFound, viewModel.uiState.value.error)
    }
}
