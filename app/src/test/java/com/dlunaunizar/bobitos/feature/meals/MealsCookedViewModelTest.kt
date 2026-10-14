package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.pantryId
import com.dlunaunizar.bobitos.data.repository.PantryFailure
import com.dlunaunizar.bobitos.data.repository.PantryRepositoryException
import com.dlunaunizar.bobitos.feature.common.FakePantryRepository
import com.dlunaunizar.bobitos.feature.common.RecordingShoppingRepository
import com.dlunaunizar.bobitos.feature.common.ingredientShoppingSource
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientBrandRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientPrefsRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
import java.time.LocalDate

// Diálogo «Has cocinado …» del ViewModel de Comidas (aparte de MealsViewModelTest, que ya es muy grande).
@OptIn(ExperimentalCoroutinesApi::class)
class MealsCookedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mealRepository = FakeMealRepository()
    private val spaceRepository = FakeSpaceRepository()
    private val recipeRepository = FakeRecipeRepository()
    private val shoppingRepository = RecordingShoppingRepository()
    private val ingredientRepository = FakeIngredientRepository()
    private val pantryRepository = FakePantryRepository()
    private val viewModel = MealsViewModel(
        mealRepository,
        spaceRepository,
        recipeRepository,
        shoppingRepository,
        ingredientShoppingSource(
            ingredientRepository,
            FakeIngredientBrandRepository(),
            FakeIngredientPrefsRepository(),
        ),
        pantryRepository,
    )

    @Test
    fun `cooking offers what the recipe uses from the pantry, read from the server`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value =
                listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Leche", "1", "l"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
            pantryRepository.serverIds = setOf(pantryId("Arroz"), pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Arroz con leche").copy(recipeId = "r1"))
            advanceUntilIdle()

            val review = viewModel.uiState.value.cooked!!
            assertEquals("Arroz con leche", review.mealName)
            assertEquals(listOf("Arroz"), review.crossOff.map(ShoppingItem::name))
            assertEquals(listOf(CookedPantryRow(pantryId("Leche"), "Leche")), review.finished)
        }

    @Test
    fun `if the pantry cannot be read the cooking dialog has no finished section`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value =
                listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Leche", "1", "l"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
            pantryRepository.itemsFailure = PantryRepositoryException(PantryFailure.Network)
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()

            assertEquals(listOf("Arroz"), viewModel.uiState.value.cooked?.crossOff?.map(ShoppingItem::name))
            assertEquals(emptyList<CookedPantryRow>(), viewModel.uiState.value.cooked?.finished)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `crossing off after cooking puts a recognized ingredient in the pantry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogState.value = listOf(CatalogIngredient("arroz", "Arroz"))
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "arroz"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()

            viewModel.confirmCooked(viewModel.uiState.value.cooked!!.defaultChoice())
            advanceUntilIdle()

            assertEquals(listOf("s1"), shoppingRepository.purchasedIds)
            assertEquals(listOf<PantryEntry?>(PantryEntry("Arroz", null)), shoppingRepository.purchasedEntries)
            assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.notice)
            assertNull(viewModel.uiState.value.cooked)
        }

    @Test
    fun `finishing after cooking removes it from the pantry and puts it on the shopping list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.cooked!!

            viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
            advanceUntilIdle()

            assertEquals(listOf(listOf(pantryId("Leche"))), pantryRepository.removed)
            assertEquals(listOf("Leche"), shoppingRepository.addedNames)
            assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.notice)
        }

    @Test
    fun `finishing without restock only removes it from the pantry`() = runTest(mainDispatcherRule.testDispatcher) {
        recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
        pantryRepository.serverIds = setOf(pantryId("Leche"))
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
        advanceUntilIdle()
        val review = viewModel.uiState.value.cooked!!

        viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")).copy(restock = false))
        advanceUntilIdle()

        assertEquals(listOf(listOf(pantryId("Leche"))), pantryRepository.removed)
        assertTrue(shoppingRepository.appliedBatches.isEmpty())
        assertTrue(shoppingRepository.addedNames.isEmpty())
    }

    @Test
    fun `confirming the cooking dialog with nothing marked writes nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value =
                listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Leche", "1", "l"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()

            viewModel.confirmCooked(CookedChoice(emptySet(), emptySet(), restock = true))
            advanceUntilIdle()

            assertTrue(shoppingRepository.purchasedIds.isEmpty())
            assertTrue(pantryRepository.removed.isEmpty())
            assertTrue(shoppingRepository.appliedBatches.isEmpty())
            assertNull(viewModel.uiState.value.cooked)
            assertFalse(viewModel.uiState.value.isSaving)
        }

    @Test
    fun `crossing off without the catalog in time waits at most 5 seconds and marks without a pantry entry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogOverride = flow { awaitCancellation() }
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
            viewModel.observe("home")
            runCurrent()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            runCurrent()
            val review = viewModel.uiState.value.cooked!!

            viewModel.confirmCooked(review.defaultChoice())
            advanceTimeBy(4_999)
            runCurrent()
            assertTrue(shoppingRepository.purchasedIds.isEmpty())

            advanceTimeBy(2)
            runCurrent()
            assertEquals(listOf("s1"), shoppingRepository.purchasedIds)
            assertEquals(listOf<PantryEntry?>(null), shoppingRepository.purchasedEntries)
        }
}
