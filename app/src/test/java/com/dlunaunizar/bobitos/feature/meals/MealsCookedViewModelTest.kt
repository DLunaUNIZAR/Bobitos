package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.SaveState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.PantryEntry
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.pantryId
import com.dlunaunizar.bobitos.data.repository.PantryFailure
import com.dlunaunizar.bobitos.data.repository.PantryRepositoryException
import com.dlunaunizar.bobitos.data.repository.ShoppingCrossOff
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.data.repository.ShoppingWrite
import com.dlunaunizar.bobitos.feature.common.FakePantryRepository
import com.dlunaunizar.bobitos.feature.common.RecordingShoppingRepository
import com.dlunaunizar.bobitos.feature.common.ingredientShoppingSource
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientBrandRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientPrefsRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
    private val viewModel by lazy {
        MealsViewModel(
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
    }

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
            assertNull(viewModel.uiState.value.save.error)
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
            assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.save.notice)
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

            assertEquals(listOf(pantryId("Leche")), shoppingRepository.cookedCalls.single().finishedPantryIds)
            assertEquals(listOf("Leche"), shoppingRepository.addedNames)
            assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.save.notice)
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

        assertEquals(listOf(pantryId("Leche")), shoppingRepository.cookedCalls.single().finishedPantryIds)
        assertTrue(shoppingRepository.cookedCalls.single().restock.isEmpty())
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
            assertTrue(shoppingRepository.cookedCalls.isEmpty())
            assertTrue(shoppingRepository.appliedBatches.isEmpty())
            assertNull(viewModel.uiState.value.cooked)
            assertFalse(viewModel.uiState.value.save.isSaving)
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

    @Test
    fun `done writes what is crossed off, what has finished and the restock in a single write`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogState.value = listOf(CatalogIngredient("arroz", "Arroz"))
            recipeRepository.mineState.value =
                listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Leche", "1", "l"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "arroz"))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Arroz con leche").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.cooked!!

            viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
            advanceUntilIdle()

            val call = shoppingRepository.cookedCalls.single()
            assertEquals(listOf(ShoppingCrossOff("s1", PantryEntry("Arroz", null))), call.crossOff)
            assertEquals(listOf(pantryId("Leche")), call.finishedPantryIds)
            assertEquals(listOf("Leche"), call.restock.map { (it as ShoppingWrite.Add).name })
            assertTrue(pantryRepository.removed.isEmpty())
            assertTrue(shoppingRepository.appliedBatches.isEmpty())
            assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.save.notice)
            assertNull(viewModel.uiState.value.cooked)
        }

    @Test
    fun `if done fails nothing is written and the dialog stays open with the error until a retry works`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.cooked!!
            shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.Network)

            viewModel.confirmCooked(review.defaultChoice())
            advanceUntilIdle()

            assertSame(review, viewModel.uiState.value.cooked)
            assertEquals(MealUiMessage.NetworkError, viewModel.uiState.value.save.error)
            assertNull(viewModel.uiState.value.save.notice)
            assertFalse(viewModel.uiState.value.save.isSaving)
            assertTrue(shoppingRepository.purchasedIds.isEmpty())

            shoppingRepository.cookedFailure = null
            viewModel.confirmCooked(review.defaultChoice())
            advanceUntilIdle()

            assertEquals(1, shoppingRepository.cookedCalls.size)
            assertNull(viewModel.uiState.value.cooked)
            assertNull(viewModel.uiState.value.save.error)
            assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.save.notice)
        }

    @Test
    fun `while done is saving the dialog stays open and a second tap does not write twice`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.cooked!!
            shoppingRepository.cookedGate = CompletableDeferred()

            viewModel.confirmCooked(review.defaultChoice())
            viewModel.confirmCooked(review.defaultChoice())
            runCurrent()

            assertSame(review, viewModel.uiState.value.cooked)
            assertTrue(viewModel.uiState.value.save.isSaving)
            shoppingRepository.cookedGate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, shoppingRepository.cookedCalls.size)
            assertNull(viewModel.uiState.value.cooked)
        }

    @Test
    fun `if an item to put back on the list was deleted meanwhile done says the list changed`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.cooked!!
            shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.ItemNotFound)

            viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
            advanceUntilIdle()

            assertEquals(MealUiMessage.CookedShoppingChanged, viewModel.uiState.value.save.error)
            assertSame(review, viewModel.uiState.value.cooked)
        }

    @Test
    fun `if the list changes while done saves a retry reads it again and writes once`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.cooked!!
            val choice = review.defaultChoice().toggleFinished(pantryId("Leche"))
            shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)

            viewModel.confirmCooked(choice)
            advanceUntilIdle()

            assertEquals(MealUiMessage.CookedShoppingChanged, viewModel.uiState.value.save.error)
            assertSame(review, viewModel.uiState.value.cooked)
            assertFalse(viewModel.uiState.value.save.isSaving)
            val readsAfterFailure = shoppingRepository.currentItemsCalls

            shoppingRepository.cookedFailure = null
            viewModel.confirmCooked(choice)
            advanceUntilIdle()

            assertEquals(readsAfterFailure + 1, shoppingRepository.currentItemsCalls)
            assertEquals(1, shoppingRepository.cookedCalls.size)
            assertNull(viewModel.uiState.value.cooked)
            assertNull(viewModel.uiState.value.save.error)
            assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.save.notice)
        }

    @Test
    fun `any other failure of done keeps its own message`() = runTest(mainDispatcherRule.testDispatcher) {
        recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
        pantryRepository.serverIds = setOf(pantryId("Leche"))
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
        advanceUntilIdle()
        val review = viewModel.uiState.value.cooked!!
        shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.PermissionDenied)

        viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
        advanceUntilIdle()

        assertEquals(MealUiMessage.PermissionDenied, viewModel.uiState.value.save.error)
        assertSame(review, viewModel.uiState.value.cooked)
    }

    @Test
    fun `cancelling after done fails takes the error away with the dialog`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.cooked!!
            shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)
            viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
            advanceUntilIdle()
            assertEquals(MealUiMessage.CookedShoppingChanged, viewModel.uiState.value.save.error)

            viewModel.dismissCooked()

            assertNull(viewModel.uiState.value.cooked)
            assertNull(viewModel.uiState.value.save.error)
        }

    @Test
    fun `done with nothing to write after a failed done takes the error away with the dialog`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.cooked!!
            shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)
            viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
            advanceUntilIdle()
            assertEquals(MealUiMessage.CookedShoppingChanged, viewModel.uiState.value.save.error)

            viewModel.confirmCooked(CookedChoice(emptySet(), emptySet(), restock = false))
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.cooked)
            assertNull(viewModel.uiState.value.save.error)
        }

    @Test
    fun `if the list changes after cancelling while done saves the notice does not ask to tap done again`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.cooked!!
            shoppingRepository.cookedGate = CompletableDeferred()
            shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)

            viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
            runCurrent()
            viewModel.dismissCooked()
            shoppingRepository.cookedGate?.complete(Unit)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.cooked)
            assertEquals(MealUiMessage.CookedNotApplied, viewModel.uiState.value.save.error)
            assertFalse(viewModel.uiState.value.save.isSaving)
            assertTrue(shoppingRepository.cookedCalls.isEmpty())
        }

    @Test
    fun `if done works after cancelling while it saves the notice says it is saved`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
            advanceUntilIdle()
            val review = viewModel.uiState.value.cooked!!
            shoppingRepository.cookedGate = CompletableDeferred()

            viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
            runCurrent()
            viewModel.dismissCooked()
            shoppingRepository.cookedGate?.complete(Unit)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.cooked)
            assertNull(viewModel.uiState.value.save.error)
            assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.save.notice)
            assertEquals(1, shoppingRepository.cookedCalls.size)
        }

    @Test
    fun `if done fails because someone changed an item the dialog names it, and only that time`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
            advanceUntilIdle()
            val choice = viewModel.uiState.value.cooked!!.defaultChoice().toggleFinished(pantryId("Leche"))
            shoppingRepository.cookedFailure =
                ShoppingRepositoryException(ShoppingFailure.ItemChanged, itemName = "Leche")

            viewModel.confirmCooked(choice)
            advanceUntilIdle()

            assertEquals(MealUiMessage.CookedShoppingChanged, viewModel.uiState.value.save.error)
            assertEquals("Leche", viewModel.uiState.value.cookedErrorItem)

            // Si el siguiente fallo no dice qué artículo, no se repite el nombre de antes.
            shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.ItemNotFound)
            viewModel.confirmCooked(choice)
            advanceUntilIdle()

            assertEquals(MealUiMessage.CookedShoppingChanged, viewModel.uiState.value.save.error)
            assertNull(viewModel.uiState.value.cookedErrorItem)
        }

    @Test
    fun `dismissing the cooked dialog forgets the changed item name`() = runTest(mainDispatcherRule.testDispatcher) {
        recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
        pantryRepository.serverIds = setOf(pantryId("Leche"))
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
        advanceUntilIdle()
        val choice = viewModel.uiState.value.cooked!!.defaultChoice().toggleFinished(pantryId("Leche"))
        shoppingRepository.cookedFailure =
            ShoppingRepositoryException(ShoppingFailure.ItemChanged, itemName = "Leche")
        viewModel.confirmCooked(choice)
        advanceUntilIdle()
        assertEquals("Leche", viewModel.uiState.value.changedItemName)

        viewModel.dismissCooked()

        assertNull(viewModel.uiState.value.changedItemName)
        assertNull(viewModel.uiState.value.save.error)
    }

    @Test
    fun `a cooked error without a name clears a previous name`() = runTest(mainDispatcherRule.testDispatcher) {
        recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
        pantryRepository.serverIds = setOf(pantryId("Leche"))
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
        advanceUntilIdle()
        val choice = viewModel.uiState.value.cooked!!.defaultChoice().toggleFinished(pantryId("Leche"))
        shoppingRepository.cookedFailure =
            ShoppingRepositoryException(ShoppingFailure.ItemChanged, itemName = "Leche")
        viewModel.confirmCooked(choice)
        advanceUntilIdle()
        assertEquals("Leche", viewModel.uiState.value.changedItemName)

        shoppingRepository.cookedFailure = ShoppingRepositoryException(ShoppingFailure.Network)
        viewModel.confirmCooked(choice)
        advanceUntilIdle()

        assertEquals(MealUiMessage.NetworkError, viewModel.uiState.value.save.error)
        assertNull(viewModel.uiState.value.changedItemName)
    }

    @Test
    fun `an item not found in the shopping list reads as the list has changed`() {
        assertEquals(
            MealUiMessage.ShoppingChanged,
            ShoppingRepositoryException(ShoppingFailure.ItemNotFound).toUiMessage(),
        )
    }

    @Test
    fun `the changed item is only named with the error that asks to tap done again`() {
        val state = MealsUiState(
            save = SaveState(error = MealUiMessage.CookedShoppingChanged),
            changedItemName = "Leche",
        )

        assertEquals("Leche", state.cookedErrorItem)
        assertNull(state.copy(save = SaveState(error = MealUiMessage.NetworkError)).cookedErrorItem)
        assertNull(state.copy(save = SaveState(error = MealUiMessage.CookedNotApplied)).cookedErrorItem)
        assertNull(state.copy(save = SaveState()).cookedErrorItem)
    }

    @Test
    fun `if someone lists meanwhile what done adds the dialog names it and a retry does not add it again`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
            advanceUntilIdle()
            val choice = viewModel.uiState.value.cooked!!.defaultChoice().toggleFinished(pantryId("Leche"))
            // Justo antes de escribir, el repositorio ve que otro miembro acaba de apuntar «Leche».
            shoppingRepository.cookedFailure =
                ShoppingRepositoryException(ShoppingFailure.ItemChanged, itemName = "Leche")

            viewModel.confirmCooked(choice)
            advanceUntilIdle()

            assertEquals(MealUiMessage.CookedShoppingChanged, viewModel.uiState.value.save.error)
            assertEquals("Leche", viewModel.uiState.value.cookedErrorItem)

            shoppingRepository.cookedFailure = null
            shoppingRepository.itemsState.value = listOf(shoppingItem("s9", "Leche"))
            viewModel.confirmCooked(choice)
            advanceUntilIdle()

            assertTrue(shoppingRepository.cookedCalls.single().restock.isEmpty())
            assertNull(viewModel.uiState.value.cooked)
            assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.save.notice)
        }

    @Test
    fun `what has finished goes on the list with the catalog name`() = runTest(mainDispatcherRule.testDispatcher) {
        ingredientRepository.catalogState.value =
            listOf(CatalogIngredient("aceite-de-oliva-virgen-extra", "Aceite de oliva virgen extra"))
        recipeRepository.mineState.value =
            listOf(recipe("r1", listOf(Ingredient("aceite de oliva virgen extra", "2", "cucharadas"))))
        pantryRepository.serverIds = setOf(pantryId("aceite de oliva virgen extra"))
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.CENA, "Pisto").copy(recipeId = "r1"))
        advanceUntilIdle()
        val review = viewModel.uiState.value.cooked!!

        viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("aceite de oliva virgen extra")))
        advanceUntilIdle()

        assertEquals(listOf("Aceite de oliva virgen extra"), shoppingRepository.addedNames)
    }

    @Test
    fun `cooking reads the list and the pantry at the same time`() = runTest(mainDispatcherRule.testDispatcher) {
        recipeRepository.mineState.value =
            listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Leche", "1", "l"))))
        shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
        pantryRepository.serverIds = setOf(pantryId("Leche"))
        viewModel.observe("home")
        advanceUntilIdle()
        shoppingRepository.currentItemsGate = CompletableDeferred()

        viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Arroz con leche").copy(recipeId = "r1"))
        runCurrent()

        assertEquals(1, shoppingRepository.currentItemsCalls)
        assertEquals(1, pantryRepository.currentIdsCalls)
        shoppingRepository.currentItemsGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("Arroz"), viewModel.uiState.value.cooked?.crossOff?.map(ShoppingItem::name))
        assertEquals(listOf(CookedPantryRow(pantryId("Leche"), "Leche")), viewModel.uiState.value.cooked?.finished)
    }

    @Test
    fun `without the catalog in time done reads the list after waiting and sees what was listed meanwhile`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogOverride = flow { awaitCancellation() }
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            runCurrent()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
            runCurrent()
            val review = viewModel.uiState.value.cooked!!

            viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
            runCurrent()
            // Mientras «Listo» espera al catálogo, otro miembro apunta «Leche».
            shoppingRepository.itemsState.value = listOf(shoppingItem("s9", "Leche"))
            advanceTimeBy(5_001)
            runCurrent()

            assertTrue(shoppingRepository.cookedCalls.single().restock.isEmpty())
            assertEquals(MealUiMessage.CookedReviewApplied, viewModel.uiState.value.save.notice)
        }

    @Test
    fun `done waits only until the catalog arrives and then reads the list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogOverride = flow {
                delay(2_000)
                emit(listOf(CatalogIngredient("leche", "Leche")))
            }
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            runCurrent()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
            runCurrent()
            val review = viewModel.uiState.value.cooked!!

            viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
            advanceTimeBy(1_000)
            runCurrent()
            assertTrue(shoppingRepository.cookedCalls.isEmpty())
            // Mientras espera, otro miembro apunta «Leche».
            shoppingRepository.itemsState.value = listOf(shoppingItem("s9", "Leche"))

            advanceTimeBy(1_001)
            runCurrent()
            assertTrue(shoppingRepository.cookedCalls.single().restock.isEmpty())
        }

    @Test
    fun `if the list cannot be read after waiting for the catalog done writes nothing and keeps the dialog`() =
        runTest(mainDispatcherRule.testDispatcher) {
            ingredientRepository.catalogOverride = flow { awaitCancellation() }
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Leche", "1", "l"))))
            pantryRepository.serverIds = setOf(pantryId("Leche"))
            viewModel.observe("home")
            runCurrent()
            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.DESAYUNO, "Café con leche").copy(recipeId = "r1"))
            runCurrent()
            val review = viewModel.uiState.value.cooked!!
            shoppingRepository.itemsFailure = ShoppingRepositoryException(ShoppingFailure.Network)

            viewModel.confirmCooked(review.defaultChoice().toggleFinished(pantryId("Leche")))
            advanceTimeBy(5_001)
            runCurrent()

            assertEquals(MealUiMessage.NetworkError, viewModel.uiState.value.save.error)
            assertFalse(viewModel.uiState.value.save.isSaving)
            assertSame(review, viewModel.uiState.value.cooked)
            assertTrue(shoppingRepository.cookedCalls.isEmpty())
        }

    @Test
    fun `done with nothing to put on the list does not read the list`() = runTest(mainDispatcherRule.testDispatcher) {
        recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
        shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
        advanceUntilIdle()
        val review = viewModel.uiState.value.cooked!!
        val readsBefore = shoppingRepository.currentItemsCalls

        viewModel.confirmCooked(review.defaultChoice())
        advanceUntilIdle()

        assertEquals(readsBefore, shoppingRepository.currentItemsCalls)
        assertEquals(1, shoppingRepository.cookedCalls.size)
    }
}
