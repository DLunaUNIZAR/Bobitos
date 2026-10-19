package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.Undoable
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.data.repository.MealFailure
import com.dlunaunizar.bobitos.data.repository.MealRepositoryException
import com.dlunaunizar.bobitos.feature.common.FakePantryRepository
import com.dlunaunizar.bobitos.feature.common.RecordingShoppingRepository
import com.dlunaunizar.bobitos.feature.common.ingredientShoppingSource
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientBrandRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientPrefsRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val viewModel by lazy {
        MealsViewModel(
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
    }

    @Test
    fun `undoing a delete keeps the id and the cooked state`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.undo(
            Undoable.Deleted("home", meal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas").copy(cooked = true)),
        )
        advanceUntilIdle()

        val (space, restored) = mealRepository.restored.single()
        assertEquals("home", space)
        assertEquals("m1", restored.id)
        assertTrue(restored.cooked)
        assertNull(viewModel.uiState.value.save.notice)
    }

    @Test
    fun `undo writes to the meal's own space`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("space-a")
        viewModel.observe("space-b")
        advanceUntilIdle()

        viewModel.undo(Undoable.Deleted("space-a", meal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas")))
        advanceUntilIdle()

        assertEquals("space-a", mealRepository.restored.single().first)
    }

    @Test
    fun `undo is offered only once the delete has finished`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        val lentejas = meal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas")
        mealRepository.nextFailure = MealRepositoryException(MealFailure.Network)
        viewModel.deleteMeal(lentejas)
        runCurrent()
        assertTrue(viewModel.undoOffers.value.isEmpty())
        assertEquals(MealUiMessage.NetworkError, viewModel.uiState.value.save.error)

        mealRepository.nextFailure = null
        viewModel.deleteMeal(lentejas)
        runCurrent()
        assertEquals(1, viewModel.undoOffers.value.size)
        assertEquals(listOf("m1"), mealRepository.deleted)
    }

    @Test
    fun `deleting offers undo with the meal and its space`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("space-a")
        val lentejas = meal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas").copy(cooked = true)
        viewModel.deleteMeal(lentejas)
        runCurrent()

        assertEquals(Undoable.Deleted("space-a", lentejas), viewModel.undoOffers.value.single().undoable)
    }

    @Test
    fun `undo of a delete goes to the space where it was deleted`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("space-a")
        viewModel.deleteMeal(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas"))
        runCurrent()
        val offered = viewModel.undoOffers.value.single().undoable
        viewModel.observe("space-b")

        viewModel.undo(offered)
        advanceUntilIdle()

        assertEquals("space-a", mealRepository.restored.single().first)
    }

    @Test
    fun `undoing an add goes through undoAddMeal`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        viewModel.undo(Undoable.Created("space-a", CreatedItem("new-1", "Cena")))
        advanceUntilIdle()

        assertEquals(listOf("space-a" to "new-1"), mealRepository.undoneAdds)
        assertTrue(mealRepository.deleted.isEmpty())
    }

    @Test
    fun `an add another member changed is not undone and says so`() = runTest(mainDispatcherRule.testDispatcher) {
        mealRepository.nextFailure = MealRepositoryException(MealFailure.MealChanged)
        viewModel.undo(Undoable.Created("home", CreatedItem("new-1", "Cena")))
        advanceUntilIdle()

        assertEquals(MealUiMessage.MealChanged, viewModel.uiState.value.save.error)
        assertTrue(mealRepository.deleted.isEmpty())
    }

    @Test
    fun `a failed undo surfaces the error`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        mealRepository.nextFailure = MealRepositoryException(MealFailure.SpaceNotFound)

        viewModel.undo(Undoable.Deleted("home", meal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas")))
        advanceUntilIdle()

        assertEquals(MealUiMessage.SpaceNotFound, viewModel.uiState.value.save.error)
    }

    @Test
    fun `restoring a meal while another write is in flight keeps saving until both end`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.observe("home")
            advanceUntilIdle()
            val addGate = CompletableDeferred<Unit>()
            val restoreGate = CompletableDeferred<Unit>()
            mealRepository.addGate = addGate
            mealRepository.restoreGate = restoreGate

            viewModel.addMeal(LocalDate.now(), MealSlot.CENA, "Cena", emptyList(), recipeId = null, cookId = null)
            runCurrent()
            viewModel.undo(Undoable.Deleted("home", meal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas")))
            runCurrent()
            assertEquals(2, viewModel.uiState.value.save.running)

            restoreGate.complete(Unit)
            runCurrent()
            assertTrue(viewModel.uiState.value.save.isSaving)

            addGate.complete(Unit)
            runCurrent()
            assertFalse(viewModel.uiState.value.save.isSaving)
        }
}
