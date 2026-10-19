package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.FailureKind
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.Undoable
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.PantryItem
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.pantryId
import com.dlunaunizar.bobitos.data.repository.MealFailure
import com.dlunaunizar.bobitos.data.repository.MealRepository
import com.dlunaunizar.bobitos.data.repository.MealRepositoryException
import com.dlunaunizar.bobitos.data.repository.PantryFailure
import com.dlunaunizar.bobitos.data.repository.PantryRepositoryException
import com.dlunaunizar.bobitos.data.repository.RecipeRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.feature.common.FakePantryRepository
import com.dlunaunizar.bobitos.feature.common.IngredientReviewChoice
import com.dlunaunizar.bobitos.feature.common.IngredientReviewRow
import com.dlunaunizar.bobitos.feature.common.RecordingShoppingRepository
import com.dlunaunizar.bobitos.feature.common.ReviewRowKind
import com.dlunaunizar.bobitos.feature.common.ingredientShoppingSource
import com.dlunaunizar.bobitos.feature.common.initialState
import com.dlunaunizar.bobitos.feature.common.toChoice
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientBrandRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientPrefsRepository
import com.dlunaunizar.bobitos.feature.ingredients.FakeIngredientRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MealsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mealRepository = FakeMealRepository()
    private val spaceRepository = FakeSpaceRepository()
    private val recipeRepository = FakeRecipeRepository()
    private val shoppingRepository = RecordingShoppingRepository()
    private val ingredientRepository = FakeIngredientRepository()
    private val brandRepository = FakeIngredientBrandRepository()
    private val ingredientPrefsRepository = FakeIngredientPrefsRepository()
    private val pantryRepository = FakePantryRepository()
    private val viewModel by lazy {
        MealsViewModel(
            mealRepository,
            spaceRepository,
            recipeRepository,
            shoppingRepository,
            ingredientShoppingSource(ingredientRepository, brandRepository, ingredientPrefsRepository),
            pantryRepository,
        )
    }

    @Test
    fun `the pantry of the space feeds the recipe picker`() = runTest(mainDispatcherRule.testDispatcher) {
        pantryRepository.itemsState.value = listOf(PantryItem("sal", "Sal", null, null))

        viewModel.observe("home")
        advanceUntilIdle()

        assertEquals("home", pantryRepository.observedSpaceId)
        assertEquals(setOf("sal"), viewModel.uiState.value.pantryIds)
    }

    @Test
    fun `an unreadable pantry leaves the picker without the chip`() = runTest(mainDispatcherRule.testDispatcher) {
        pantryRepository.itemsFailure = PantryRepositoryException(PantryFailure.PermissionDenied)
        mealRepository.mealsState.value = listOf(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas"))

        viewModel.observe("home")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pantryIds)
        assertNull(viewModel.uiState.value.save.error)
        assertEquals(listOf("Lentejas"), (viewModel.uiState.value.meals as UiState.Content).value.map(Meal::name))
    }

    @Test
    fun `observes meals and members for the active space`() = runTest(mainDispatcherRule.testDispatcher) {
        spaceRepository.membersState.value = listOf(SpaceMember("ana", "Ana", SpaceRole.MEMBER))
        mealRepository.mealsState.value = listOf(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas"))

        viewModel.observe("home")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("home", mealRepository.observedSpaceId)
        assertEquals(listOf("Lentejas"), (state.meals as UiState.Content).value.map(Meal::name))
        assertEquals(listOf("Ana"), (state.members as UiState.Content).value.map(SpaceMember::displayName))
    }

    @Test
    fun `invalid meal never reaches repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addMeal(LocalDate.now(), MealSlot.CENA, "   ", emptyList(), recipeId = null, cookId = null)

        assertEquals(0, mealRepository.addCount)
        assertEquals(MealUiMessage.NameRequired, viewModel.uiState.value.save.editorError)
        assertNull(viewModel.uiState.value.save.error)
    }

    @Test
    fun `adding a meal trims the name and reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addMeal(
            LocalDate.now(),
            MealSlot.DESAYUNO,
            "  Tostadas  ",
            listOf("ana"),
            recipeId = null,
            cookId = null,
        )
        advanceUntilIdle()

        assertEquals("Tostadas", mealRepository.addedName)
        assertEquals(1, mealRepository.addCount)
        assertNull(viewModel.uiState.value.save.notice)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun creatingOneItemOffersUndoWithItsIdAndName() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addMeal(LocalDate.now(), MealSlot.COMIDA, "  Lentejas ", emptyList(), recipeId = null, cookId = null)
        runCurrent()

        val offer = viewModel.undoOffers.value.single()
        assertEquals(Undoable.Created("home", CreatedItem("new-1", "Lentejas")), offer.undoable)
        assertNull(viewModel.uiState.value.save.notice)

        viewModel.undoControls.consumeOffer(offer.serial)
        assertTrue(viewModel.undoOffers.value.isEmpty())
    }

    @Test
    fun editingDoesNotOfferUndo() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        viewModel.updateMeal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas", emptyList(), null, null)
        advanceUntilIdle()

        assertTrue(viewModel.undoOffers.value.isEmpty())
    }

    @Test
    fun duplicatingADayNeverOffersUndo() = runTest(mainDispatcherRule.testDispatcher) {
        val today = LocalDate.now()
        mealRepository.mealsState.value = listOf(meal("m1", today, MealSlot.COMIDA, "Lentejas"))
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.duplicateDay(today.plusDays(1))
        advanceUntilIdle()

        assertTrue(viewModel.undoOffers.value.isEmpty())
        assertEquals(MealUiMessage.MealsDuplicated, viewModel.uiState.value.save.notice)
    }

    @Test
    fun aRestoredMealNeverOffersUndo() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.undo(Undoable.Deleted("home", meal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas")))
        advanceUntilIdle()

        assertTrue(viewModel.undoOffers.value.isEmpty())
    }

    @Test
    fun `adding a meal from a recipe stores its id`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addMeal(LocalDate.now(), MealSlot.COMIDA, "Paella", emptyList(), recipeId = "receta-1", cookId = null)
        advanceUntilIdle()

        assertEquals("receta-1", mealRepository.addedRecipeId)
        assertNull(viewModel.uiState.value.save.notice)
    }

    @Test
    fun `adding a meal with a cook passes the cook id`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addMeal(
            LocalDate.now(),
            MealSlot.COMIDA,
            "Lentejas",
            listOf("ana", "leo"),
            recipeId = null,
            cookId = "leo",
        )
        advanceUntilIdle()

        assertEquals("leo", mealRepository.addedCookId)
        assertNull(viewModel.uiState.value.save.notice)
    }

    @Test
    fun aLoadFailureNeverExposesTheExceptionText() = runTest(mainDispatcherRule.testDispatcher) {
        mealRepository.loadFailure = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")

        viewModel.observe("home")
        advanceUntilIdle()

        assertEquals(UiState.Error(FailureKind.UNKNOWN), viewModel.uiState.value.meals)
    }

    @Test
    fun `network failure is shown explicitly`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()
        mealRepository.nextFailure = MealRepositoryException(MealFailure.Network)

        viewModel.deleteMeal(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas"))
        advanceUntilIdle()

        assertEquals(MealUiMessage.NetworkError, viewModel.uiState.value.save.error)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `reviewing then confirming pushes a recipe's ingredients to the shopping list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(
                recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Sal"))),
            )
            viewModel.observe("home")
            advanceUntilIdle()

            val meal = meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1")
            viewModel.addIngredientsToShopping(meal)
            advanceUntilIdle()

            val review = viewModel.uiState.value.ingredientReview
            assertEquals(listOf("Arroz", "Sal"), review?.map(IngredientReviewRow::name))

            viewModel.confirmIngredientReview(listOf(choice("300"), choice(null)))
            advanceUntilIdle()

            assertEquals(listOf("Arroz", "Sal"), shoppingRepository.addedNames)
            assertEquals(listOf<String?>(null, null), shoppingRepository.addedNotes)
            assertEquals(listOf<String?>("300", null), shoppingRepository.addedQuantities)
            assertEquals(MealUiMessage.IngredientsAddedToShopping, viewModel.uiState.value.save.notice)
        }

    @Test
    fun `confirming the review adds each new row with its chosen brand and store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(
                recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Sal"))),
            )
            ingredientRepository.catalogState.value = listOf(CatalogIngredient("arroz", "Arroz"))
            brandRepository.brandsState.value = listOf(
                IngredientBrand("b1", "arroz", "Brillante", favorite = true, stores = listOf(Supermarket.DIA)),
            )
            ingredientPrefsRepository.state.value = mapOf(
                "arroz" to IngredientPref(listOf(Supermarket.MERCADONA)),
            )
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.addIngredientsToShopping(
                meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"),
            )
            advanceUntilIdle()
            val review = viewModel.uiState.value.ingredientReview!!
            assertEquals("arroz", review[0].context?.ingredient?.id)
            assertEquals(null, review[1].context)

            viewModel.confirmIngredientReview(
                listOf(
                    IngredientReviewChoice("300", Supermarket.DIA, "Brillante"),
                    IngredientReviewChoice(null, null, null),
                ),
            )
            advanceUntilIdle()

            assertEquals(listOf("Arroz", "Sal"), shoppingRepository.addedNames)
            assertEquals(listOf(Supermarket.DIA, null), shoppingRepository.addedSupermarkets)
            assertEquals(listOf<String?>("Brillante", null), shoppingRepository.addedBrands)
        }

    @Test
    fun `marking a meal cooked offers to cross off its ingredients from the list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
            viewModel.observe("home")
            advanceUntilIdle()

            val cooked = meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1")
            viewModel.markCooked(cooked)
            advanceUntilIdle()

            assertEquals("m1", mealRepository.cookedMealId)
            assertEquals(true, mealRepository.cookedValue)
            assertEquals(listOf("Arroz"), viewModel.uiState.value.cooked?.crossOff?.map(ShoppingItem::name))

            viewModel.confirmCooked(viewModel.uiState.value.cooked!!.defaultChoice())
            advanceUntilIdle()

            assertEquals(listOf("s1"), shoppingRepository.purchasedIds)
            assertEquals(null, viewModel.uiState.value.cooked)
        }

    @Test
    fun `duplicating a day copies its meals to the target date`() = runTest(mainDispatcherRule.testDispatcher) {
        val today = LocalDate.now()
        mealRepository.mealsState.value = listOf(
            meal("m1", today, MealSlot.COMIDA, "Lentejas"),
            meal("m2", today, MealSlot.CENA, "Sopa"),
        )
        viewModel.observe("home")
        advanceUntilIdle()

        val target = today.plusDays(3)
        viewModel.duplicateDay(target)
        advanceUntilIdle()

        assertEquals(2, mealRepository.addCount)
        assertEquals(listOf(target, target), mealRepository.addedDates)
        assertEquals(MealUiMessage.MealsDuplicated, viewModel.uiState.value.save.notice)
    }

    @Test
    fun `the day's ingredient review dedups by name`() = runTest(mainDispatcherRule.testDispatcher) {
        val today = LocalDate.now()
        recipeRepository.mineState.value = listOf(
            recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Sal"))),
            recipe("r2", listOf(Ingredient("arroz", "1", "kg"), Ingredient("Aceite"))),
        )
        mealRepository.mealsState.value = listOf(
            meal("m1", today, MealSlot.COMIDA, "Paella").copy(recipeId = "r1"),
            meal("m2", today, MealSlot.CENA, "Arroz al horno").copy(recipeId = "r2"),
        )
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addDayIngredientsToShopping()
        advanceUntilIdle()

        val review = viewModel.uiState.value.ingredientReview
        assertEquals(listOf("Arroz", "Sal", "Aceite"), review?.map(IngredientReviewRow::name))

        viewModel.confirmIngredientReview(review!!.map { choice(it.recipeQuantity) })
        advanceUntilIdle()

        assertEquals(listOf("Arroz", "Sal", "Aceite"), shoppingRepository.addedNames)
        assertEquals(MealUiMessage.IngredientsAddedToShopping, viewModel.uiState.value.save.notice)
    }

    @Test
    fun `the review aggregates all quantities of a repeated ingredient`() = runTest(mainDispatcherRule.testDispatcher) {
        val today = LocalDate.now()
        recipeRepository.mineState.value = listOf(
            recipe("r1", listOf(Ingredient("Arroz", "200", "g"))),
            recipe("r2", listOf(Ingredient("arroz", "300", "g"))),
        )
        mealRepository.mealsState.value = listOf(
            meal("m1", today, MealSlot.COMIDA, "A").copy(recipeId = "r1"),
            meal("m2", today, MealSlot.CENA, "B").copy(recipeId = "r2"),
        )
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addDayIngredientsToShopping()
        advanceUntilIdle()

        val review = viewModel.uiState.value.ingredientReview!!
        assertEquals(1, review.size)
        assertEquals(listOf("200 g", "300 g"), review[0].quantities)
        assertEquals("500 g", review[0].recipeQuantity)
    }

    @Test
    fun `confirming with every row omitted writes nothing and says it was all in the list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "arroz", quantity = "1 kg"))
            viewModel.observe("home")
            advanceUntilIdle()

            val meal = meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1")
            viewModel.addIngredientsToShopping(meal)
            advanceUntilIdle()
            val review = viewModel.uiState.value.ingredientReview!!
            viewModel.confirmIngredientReview(review.map { it.initialState().toChoice(it) })
            advanceUntilIdle()

            assertTrue(shoppingRepository.updatedIds.isEmpty())
            assertTrue(shoppingRepository.addedNames.isEmpty())
            assertEquals(MealUiMessage.NothingToAddToShopping, viewModel.uiState.value.save.notice)
            assertEquals(null, viewModel.uiState.value.ingredientReview)
        }

    @Test
    fun `marking cooked offers items whose name differs only in plural`() = runTest(mainDispatcherRule.testDispatcher) {
        recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Tomates", "3", "ud"))))
        shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Tomate"))
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Ensalada").copy(recipeId = "r1"))
        advanceUntilIdle()

        assertEquals(listOf("Tomate"), viewModel.uiState.value.cooked?.crossOff?.map(ShoppingItem::name))
    }

    @Test
    fun `changing week re-observes the range`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()
        val initialWeek = viewModel.uiState.value.weekStart

        viewModel.nextWeek()
        advanceUntilIdle()

        assertEquals(initialWeek.plusWeeks(1), viewModel.uiState.value.weekStart)
        assertEquals(initialWeek.plusWeeks(1), mealRepository.lastWeekStart)
    }

    @Test
    fun `editor save is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        mealRepository.addGate = gate
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addMeal(LocalDate.now(), MealSlot.CENA, "Cena", emptyList(), recipeId = null, cookId = null)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.save.editor)
        assertTrue(viewModel.uiState.value.save.isSaving)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.save.editor)
        assertFalse(viewModel.uiState.value.save.isSaving)
        viewModel.feedback.resetEditor()
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.save.editor)
    }

    @Test
    fun `editor save failure or timeout leaves FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()
        mealRepository.nextFailure = MealRepositoryException(MealFailure.Network)
        viewModel.addMeal(LocalDate.now(), MealSlot.CENA, "Cena", emptyList(), recipeId = null, cookId = null)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        assertEquals(MealUiMessage.NetworkError, viewModel.uiState.value.save.editorError)
        assertNull(viewModel.uiState.value.save.error)
        assertFalse(viewModel.uiState.value.save.isSaving)

        mealRepository.nextFailure = null
        viewModel.feedback.resetEditor()
        mealRepository.hang = true
        viewModel.addMeal(LocalDate.now(), MealSlot.CENA, "Cena", emptyList(), recipeId = null, cookId = null)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.save.editor)
        advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        assertEquals(MealUiMessage.SaveTimeout, viewModel.uiState.value.save.editorError)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `duplicating the week completes every copy even when the repository is slow`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val today = LocalDate.now()
            mealRepository.mealsState.value = listOf(
                meal("m1", today, MealSlot.COMIDA, "Lentejas"),
                meal("m2", today, MealSlot.CENA, "Sopa"),
                meal("m3", today, MealSlot.DESAYUNO, "Tostadas"),
            )
            mealRepository.addDelayMillis = 10_000
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.duplicateWeekToNext()
            advanceUntilIdle()

            assertEquals(3, mealRepository.addCount)
            assertEquals(MealUiMessage.MealsDuplicated, viewModel.uiState.value.save.notice)
            assertEquals(null, viewModel.uiState.value.save.error)
        }

    @Test
    fun `restoreMeal does not touch the editor status`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.undo(Undoable.Deleted("home", meal("m1", LocalDate.now(), MealSlot.COMIDA, "Lentejas")))
        advanceUntilIdle()

        assertEquals("Lentejas", mealRepository.restored.single().second.name)
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.save.editor)
        assertNull(viewModel.uiState.value.save.notice)
    }

    @Test
    fun `a shopping list changed while reviewing is reported`() = runTest(mainDispatcherRule.testDispatcher) {
        recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
        viewModel.observe("home")
        advanceUntilIdle()
        viewModel.addIngredientsToShopping(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
        advanceUntilIdle()
        shoppingRepository.applyFailure = ShoppingRepositoryException(ShoppingFailure.ItemChanged)

        viewModel.confirmIngredientReview(listOf(choice("300 g")))
        advanceUntilIdle()

        assertEquals(MealUiMessage.ShoppingChanged, viewModel.uiState.value.save.error)
    }

    @Test
    fun `the review reads the list from the server, not from a stale cached snapshot`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "arroz", quantity = "1 kg"))
            shoppingRepository.serverItems =
                listOf(shoppingItem("s1", "arroz", quantity = "1 kg").copy(purchased = true))
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.addIngredientsToShopping(
                meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"),
            )
            advanceUntilIdle()

            assertEquals(ReviewRowKind.PURCHASED, viewModel.uiState.value.ingredientReview!!.single().kind)
        }

    @Test
    fun `a shopping item deleted while reviewing is reported as a changed list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addIngredientsToShopping(
                meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"),
            )
            advanceUntilIdle()
            shoppingRepository.applyFailure = ShoppingRepositoryException(ShoppingFailure.ItemNotFound)

            viewModel.confirmIngredientReview(listOf(choice("300 g")))
            advanceUntilIdle()

            assertEquals(MealUiMessage.ShoppingChanged, viewModel.uiState.value.save.error)
        }

    @Test
    fun `if the shopping list cannot be read the review does not open and the error is shown`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsFailure = ShoppingRepositoryException(ShoppingFailure.Network)
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.addIngredientsToShopping(
                meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"),
            )
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.ingredientReview)
            assertEquals(MealUiMessage.NetworkError, viewModel.uiState.value.save.error)
        }

    @Test
    fun `while the shopping list is being read a second request is ignored and isSaving is on`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            viewModel.observe("home")
            advanceUntilIdle()
            val gate = CompletableDeferred<Unit>()
            shoppingRepository.currentItemsGate = gate
            val paella = meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1")

            viewModel.addIngredientsToShopping(paella)
            viewModel.addIngredientsToShopping(paella)
            runCurrent()

            assertEquals(1, shoppingRepository.currentItemsCalls)
            assertTrue(viewModel.uiState.value.save.isSaving)
            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.save.isSaving)
            assertEquals(listOf("Arroz"), viewModel.uiState.value.ingredientReview?.map { it.name })
        }

    @Test
    fun `if the shopping list cannot be read isSaving goes off`() = runTest(mainDispatcherRule.testDispatcher) {
        recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
        shoppingRepository.itemsFailure = ShoppingRepositoryException(ShoppingFailure.Network)
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addIngredientsToShopping(
            meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"),
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.save.isSaving)
        assertEquals(MealUiMessage.NetworkError, viewModel.uiState.value.save.error)
    }

    @Test
    fun `a recipe whose ingredients were all bought writes nothing by default`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsState.value =
                listOf(shoppingItem("s1", "arroz", quantity = "1 kg").copy(purchased = true))
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addIngredientsToShopping(
                meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"),
            )
            advanceUntilIdle()
            val review = viewModel.uiState.value.ingredientReview!!

            viewModel.confirmIngredientReview(review.map { it.initialState().toChoice(it) })
            advanceUntilIdle()

            assertTrue(shoppingRepository.appliedBatches.isEmpty())
            assertEquals(MealUiMessage.NothingToAddToShopping, viewModel.uiState.value.save.notice)
        }

    @Test
    fun `marking cooked reads the list from the server, not from a stale cached snapshot`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value =
                listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Tomate", "2", "ud"))))
            shoppingRepository.itemsState.value = listOf(shoppingItem("s1", "Arroz"))
            shoppingRepository.serverItems =
                listOf(shoppingItem("s1", "Arroz").copy(purchased = true), shoppingItem("s2", "Tomate"))
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()

            assertEquals(listOf("Tomate"), viewModel.uiState.value.cooked?.crossOff?.map(ShoppingItem::name))
        }

    @Test
    fun `marking cooked when the list cannot be read still marks the meal and offers nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            shoppingRepository.itemsFailure = ShoppingRepositoryException(ShoppingFailure.Network)
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.markCooked(meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"))
            advanceUntilIdle()

            assertEquals(true, mealRepository.cookedValue)
            assertEquals(MealUiMessage.MealCooked, viewModel.uiState.value.save.notice)
            assertEquals(null, viewModel.uiState.value.cooked)
            assertEquals(null, viewModel.uiState.value.save.error)
        }

    @Test
    fun `the meal review marks what is in the pantry, read from the server`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value =
                listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Tomate", "2", "ud"))))
            pantryRepository.serverIds = setOf(pantryId("Arroz"))
            viewModel.observe("home")
            advanceUntilIdle()

            val paella = meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1")
            viewModel.addIngredientsToShopping(paella)
            advanceUntilIdle()

            assertEquals(
                listOf(ReviewRowKind.IN_PANTRY, ReviewRowKind.NEW),
                viewModel.uiState.value.ingredientReview?.map { it.kind },
            )
        }

    @Test
    fun `if the pantry cannot be read the meal review opens without it`() = runTest(mainDispatcherRule.testDispatcher) {
        recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
        pantryRepository.itemsFailure = PantryRepositoryException(PantryFailure.Network)
        viewModel.observe("home")
        advanceUntilIdle()

        val paella = meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1")
        viewModel.addIngredientsToShopping(paella)
        advanceUntilIdle()

        assertEquals(listOf(ReviewRowKind.NEW), viewModel.uiState.value.ingredientReview?.map { it.kind })
        assertNull(viewModel.uiState.value.save.error)
    }

    @Test
    fun `the meal review reads the list and the pantry at the same time`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            pantryRepository.serverIds = setOf(pantryId("Arroz"))
            viewModel.observe("home")
            advanceUntilIdle()
            shoppingRepository.currentItemsGate = CompletableDeferred()

            val paella = meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1")
            viewModel.addIngredientsToShopping(paella)
            runCurrent()

            assertEquals(1, shoppingRepository.currentItemsCalls)
            assertEquals(1, pantryRepository.currentIdsCalls)
            shoppingRepository.currentItemsGate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(ReviewRowKind.IN_PANTRY), viewModel.uiState.value.ingredientReview?.map { it.kind })
        }

    // Ya pasa hoy (la lista se lee primero); protege la versión en paralelo de esperar a la despensa.
    @Test
    fun `if the list cannot be read the meal review fails without waiting for the pantry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(recipe("r1", listOf(Ingredient("Arroz", "300", "g"))))
            viewModel.observe("home")
            advanceUntilIdle()
            shoppingRepository.itemsFailure = ShoppingRepositoryException(ShoppingFailure.Network)
            pantryRepository.currentIdsGate = CompletableDeferred()

            val paella = meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1")
            viewModel.addIngredientsToShopping(paella)
            runCurrent()

            assertEquals(MealUiMessage.NetworkError, viewModel.uiState.value.save.error)
            assertNull(viewModel.uiState.value.ingredientReview)
            assertFalse(viewModel.uiState.value.save.isSaving)
        }
}

internal class FakeMealRepository : MealRepository {
    var addGate: CompletableDeferred<Unit>? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    // Cada alta tarda esto en tiempo virtual (repositorio lento).
    var addDelayMillis = 0L

    var observedSpaceId: String? = null
    var lastWeekStart: LocalDate? = null
    var addedName: String? = null
    var addedRecipeId: String? = null
    var addedCookId: String? = null
    var addCount = 0
    val addedDates = mutableListOf<LocalDate>()
    var nextFailure: MealRepositoryException? = null
    var loadFailure: Throwable? = null
    val mealsState = MutableStateFlow<List<Meal>>(emptyList())

    override fun meals(spaceId: String, weekStart: LocalDate, weekEndExclusive: LocalDate): Flow<List<Meal>> {
        observedSpaceId = spaceId
        lastWeekStart = weekStart
        loadFailure?.let { return flow { throw it } }
        return mealsState
    }

    override suspend fun addMeal(
        spaceId: String,
        date: LocalDate,
        slot: MealSlot,
        name: String,
        participantIds: List<String>,
        recipeId: String?,
        cookId: String?,
    ): String {
        throwNextFailure()
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                throw MealRepositoryException(MealFailure.Unknown)
            }
        }
        addGate?.await()
        if (addDelayMillis > 0) delay(addDelayMillis)
        addedName = name
        addedRecipeId = recipeId
        addedCookId = cookId
        addedDates += date
        addCount++
        return "new-1"
    }

    override suspend fun updateMeal(
        spaceId: String,
        mealId: String,
        date: LocalDate,
        slot: MealSlot,
        name: String,
        participantIds: List<String>,
        recipeId: String?,
        cookId: String?,
    ) {
        throwNextFailure()
    }

    var cookedMealId: String? = null
    var cookedValue: Boolean? = null
    override suspend fun setCooked(spaceId: String, mealId: String, cooked: Boolean) {
        throwNextFailure()
        cookedMealId = mealId
        cookedValue = cooked
    }

    val restored = mutableListOf<Pair<String, Meal>>()

    var restoreGate: CompletableDeferred<Unit>? = null

    override suspend fun restoreMeal(spaceId: String, meal: Meal) {
        throwNextFailure()
        restoreGate?.await()
        restored += spaceId to meal
    }

    val undoneAdds = mutableListOf<Pair<String, String>>()

    override suspend fun undoAddMeal(spaceId: String, mealId: String) {
        throwNextFailure()
        undoneAdds += spaceId to mealId
    }

    val deleted = mutableListOf<String>()

    override suspend fun deleteMeal(spaceId: String, mealId: String) {
        throwNextFailure()
        deleted += mealId
    }

    private fun throwNextFailure() {
        nextFailure?.let { throw it }
    }
}

internal class FakeSpaceRepository : SpaceRepository {
    val membersState = MutableStateFlow<List<SpaceMember>>(emptyList())

    override fun members(spaceId: String): Flow<List<SpaceMember>> = membersState
    override fun spaces(): Flow<List<SpaceSummary>> = error("no usado en el test")
    override fun space(spaceId: String): Flow<SpaceSummary?> = error("no usado en el test")
    override fun invitations(spaceId: String): Flow<List<SpaceInvitation>> = error("no usado en el test")
    override suspend fun createSpace(name: String): String = error("no usado en el test")
    override suspend fun renameSpace(spaceId: String, name: String) {
        error("no usado en el test")
    }
    override suspend fun leaveSpace(spaceId: String) {
        error("no usado en el test")
    }
    override suspend fun removeMember(spaceId: String, userId: String) {
        error("no usado en el test")
    }
    override suspend fun transferOwnership(spaceId: String, newOwnerId: String) {
        error("no usado en el test")
    }
    override suspend fun deleteSpace(spaceId: String) {
        error("no usado en el test")
    }
    override suspend fun createInvitation(spaceId: String): SpaceInvitation = error("no usado en el test")
    override suspend fun revokeInvitation(invitationId: String) {
        error("no usado en el test")
    }
    override suspend fun acceptInvitation(code: String): String = error("no usado en el test")
}

internal class FakeRecipeRepository : RecipeRepository {
    val mineState = MutableStateFlow<List<Recipe>>(emptyList())
    override fun globalRecipes(): Flow<List<Recipe>> = MutableStateFlow(emptyList())
    override fun myRecipes(): Flow<List<Recipe>> = mineState
    override fun isCurrentUserRecipeAdmin(): Boolean = false
    override suspend fun createRecipe(
        visibility: RecipeVisibility,
        title: String,
        description: String?,
        category: String?,
        sourceRecipeId: String?,
        ingredients: List<Ingredient>,
        sourceUrl: String?,
        steps: List<String>,
        servings: Int?,
        totalMinutes: Int?,
    ): String = "new-1"

    override suspend fun updateRecipe(
        recipeId: String,
        title: String,
        description: String?,
        category: String?,
        ingredients: List<Ingredient>,
        steps: List<String>,
        servings: Int?,
        totalMinutes: Int?,
    ) = Unit

    override suspend fun deleteRecipe(recipeId: String) = Unit

    override suspend fun restoreRecipe(recipe: Recipe) = Unit
}

private fun choice(quantity: String?) = IngredientReviewChoice(quantity, null, null)

internal fun recipe(id: String, ingredients: List<Ingredient>) = Recipe(
    id = id,
    ownerUid = "owner",
    visibility = RecipeVisibility.PRIVATE,
    title = "Receta",
    description = null,
    category = null,
    ingredients = ingredients,
    createdBy = "owner",
    createdByName = "David",
    createdAt = Instant.EPOCH,
    updatedBy = "owner",
    updatedAt = Instant.EPOCH,
)

internal fun meal(id: String, date: LocalDate, slot: MealSlot, name: String) = Meal(
    id = id,
    date = date,
    slot = slot,
    name = name,
    participantIds = emptyList(),
    participantNames = emptyList(),
    createdBy = "owner",
    createdByName = "David",
    createdAt = Instant.EPOCH,
    updatedBy = "owner",
    updatedAt = Instant.EPOCH,
)

internal fun shoppingItem(id: String, name: String, quantity: String? = null) = ShoppingItem(
    id = id,
    name = name,
    quantity = quantity,
    notes = null,
    purchased = false,
    createdBy = "owner",
    createdByName = "David",
    createdAt = Instant.EPOCH,
    updatedBy = "owner",
    updatedAt = Instant.EPOCH,
    purchasedBy = null,
    purchasedByName = null,
    purchasedAt = null,
)
