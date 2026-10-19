package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.Undoable
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.pantryId
import com.dlunaunizar.bobitos.data.repository.MealFailure
import com.dlunaunizar.bobitos.data.repository.MealRepositoryException
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.feature.common.FakePantryRepository
import com.dlunaunizar.bobitos.feature.common.RecordingShoppingRepository
import com.dlunaunizar.bobitos.feature.common.ingredientShoppingSource
import com.dlunaunizar.bobitos.feature.common.initialState
import com.dlunaunizar.bobitos.feature.common.toChoice
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientBrandRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientPrefsRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

// Errores y avisos pendientes de otras acciones frente a «Listo» y la revisión de la compra (ViewModel de Comidas).
@OptIn(ExperimentalCoroutinesApi::class)
class MealsPendingFeedbackViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mealRepository = FakeMealRepository()
    private val recipeRepository = FakeRecipeRepository()
    private val shoppingRepository = RecordingShoppingRepository()
    private val pantryRepository = FakePantryRepository()
    private val viewModel by lazy {
        MealsViewModel(
            mealRepository,
            FakeSpaceRepository(),
            recipeRepository,
            shoppingRepository,
            ingredientShoppingSource(
                FakeIngredientRepository(),
                FakeIngredientBrandRepository(),
                FakeIngredientPrefsRepository(),
            ),
            pantryRepository,
        )
    }

    @Test
    fun `done refused because another action is saving keeps the dialog error and writes nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
            advanceUntilIdle()
            val choice = viewModel.uiState.value.cooked!!.defaultChoice().toggleFinished(pantryId("Leche"))
            shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)
            viewModel.confirmCooked(choice)
            advanceUntilIdle()
            assertEquals(MealUiMessage.CookedShoppingChanged, viewModel.uiState.value.save.error)
            shoppingRepository.cookedFailure = null
            mealRepository.restoreGate = CompletableDeferred()
            viewModel.undo(Undoable.Deleted("home", meal("m2", LocalDate.now(), MealSlot.COMIDA, "Lentejas")))
            runCurrent()
            val callsBefore = shoppingRepository.cookedCalls.size

            viewModel.confirmCooked(choice)
            runCurrent()

            assertEquals(MealUiMessage.CookedShoppingChanged, viewModel.uiState.value.save.error)
            assertEquals(callsBefore, shoppingRepository.cookedCalls.size)
            mealRepository.restoreGate?.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `confirming an empty review keeps the pending error of another action`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "arroz", quantity = "1 kg"))
            viewModel.observe("home")
            advanceUntilIdle()
            val meal = meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1")
            viewModel.addIngredientsToShopping(meal)
            advanceUntilIdle()
            val review = viewModel.uiState.value.ingredientReview!!
            mealRepository.nextFailure = MealRepositoryException(MealFailure.SpaceNotFound)
            viewModel.undo(Undoable.Deleted("home", meal))
            advanceUntilIdle()
            assertEquals(MealUiMessage.SpaceNotFound, viewModel.uiState.value.save.error)

            viewModel.confirmIngredientReview(review.map { it.initialState().toChoice(it) })
            advanceUntilIdle()

            assertEquals(MealUiMessage.SpaceNotFound, viewModel.uiState.value.save.error)
            assertEquals(MealUiMessage.NothingToAddToShopping, viewModel.uiState.value.save.notice)
        }
}
