package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.MealFailure
import com.dlunaunizar.bobitos.data.repository.MealRepository
import com.dlunaunizar.bobitos.data.repository.MealRepositoryException
import com.dlunaunizar.bobitos.data.repository.RecipeRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.feature.common.IngredientReviewRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val shoppingRepository = FakeShoppingRepository()
    private val ingredientPrefsRepository = FakeIngredientPrefsRepository()
    private val viewModel = MealsViewModel(
        mealRepository,
        spaceRepository,
        recipeRepository,
        shoppingRepository,
        ingredientPrefsRepository,
    )

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

        viewModel.addMeal(LocalDate.now(), MealSlot.CENA, "   ", emptyList(), recipeId = null)

        assertEquals(0, mealRepository.addCount)
        assertEquals(MealUiMessage.NameRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `adding a meal trims the name and reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addMeal(LocalDate.now(), MealSlot.DESAYUNO, "  Tostadas  ", listOf("ana"), recipeId = null)
        advanceUntilIdle()

        assertEquals("Tostadas", mealRepository.addedName)
        assertEquals(1, mealRepository.addCount)
        assertEquals(MealUiMessage.MealAdded, viewModel.uiState.value.notice)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `adding a meal from a recipe stores its id`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addMeal(LocalDate.now(), MealSlot.COMIDA, "Paella", emptyList(), recipeId = "receta-1")
        advanceUntilIdle()

        assertEquals("receta-1", mealRepository.addedRecipeId)
        assertEquals(MealUiMessage.MealAdded, viewModel.uiState.value.notice)
    }

    @Test
    fun `network failure is shown explicitly`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()
        mealRepository.nextFailure = MealRepositoryException(MealFailure.Network)

        viewModel.deleteMeal("m1")
        advanceUntilIdle()

        assertEquals(MealUiMessage.NetworkError, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
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

            viewModel.confirmIngredientReview(listOf("300", null))
            advanceUntilIdle()

            assertEquals(listOf("Arroz", "Sal"), shoppingRepository.addedNames)
            assertEquals(listOf("g", null), shoppingRepository.addedNotes)
            assertEquals(MealUiMessage.IngredientsAddedToShopping, viewModel.uiState.value.notice)
        }

    @Test
    fun `dumping ingredients applies the user's default supermarket and brand`() =
        runTest(mainDispatcherRule.testDispatcher) {
            recipeRepository.mineState.value = listOf(
                recipe("r1", listOf(Ingredient("Arroz", "300", "g"), Ingredient("Sal"))),
            )
            // slug("Arroz") == "arroz"; slug("Sal") == "sal" (sin preferencia).
            ingredientPrefsRepository.prefsState.value = mapOf(
                "arroz" to IngredientPref(Supermarket.MERCADONA, "Hacendado"),
            )
            viewModel.observe("home")
            advanceUntilIdle()

            viewModel.addIngredientsToShopping(
                meal("m1", LocalDate.now(), MealSlot.COMIDA, "Paella").copy(recipeId = "r1"),
            )
            advanceUntilIdle()
            viewModel.confirmIngredientReview(listOf("300", null))
            advanceUntilIdle()

            assertEquals(listOf("Arroz", "Sal"), shoppingRepository.addedNames)
            assertEquals(listOf(Supermarket.MERCADONA, null), shoppingRepository.addedSupermarkets)
            assertEquals(listOf("Hacendado", null), shoppingRepository.addedBrands)
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
        assertEquals(MealUiMessage.MealsDuplicated, viewModel.uiState.value.notice)
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

        viewModel.confirmIngredientReview(review!!.map(IngredientReviewRow::recipeQuantity))
        advanceUntilIdle()

        assertEquals(listOf("Arroz", "Sal", "Aceite"), shoppingRepository.addedNames)
        assertEquals(MealUiMessage.IngredientsAddedToShopping, viewModel.uiState.value.notice)
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
        assertEquals("200 g + 300 g", review[0].recipeQuantity)
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
}

private class FakeMealRepository : MealRepository {
    var observedSpaceId: String? = null
    var lastWeekStart: LocalDate? = null
    var addedName: String? = null
    var addedRecipeId: String? = null
    var addCount = 0
    val addedDates = mutableListOf<LocalDate>()
    var nextFailure: MealRepositoryException? = null
    val mealsState = MutableStateFlow<List<Meal>>(emptyList())

    override fun meals(spaceId: String, weekStart: LocalDate, weekEndExclusive: LocalDate): Flow<List<Meal>> {
        observedSpaceId = spaceId
        lastWeekStart = weekStart
        return mealsState
    }

    override suspend fun addMeal(
        spaceId: String,
        date: LocalDate,
        slot: MealSlot,
        name: String,
        participantIds: List<String>,
        recipeId: String?,
    ) {
        throwNextFailure()
        addedName = name
        addedRecipeId = recipeId
        addedDates += date
        addCount++
    }

    override suspend fun updateMeal(
        spaceId: String,
        mealId: String,
        date: LocalDate,
        slot: MealSlot,
        name: String,
        participantIds: List<String>,
        recipeId: String?,
    ) {
        throwNextFailure()
    }

    override suspend fun deleteMeal(spaceId: String, mealId: String) {
        throwNextFailure()
    }

    private fun throwNextFailure() {
        nextFailure?.let { throw it }
    }
}

private class FakeSpaceRepository : SpaceRepository {
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

private class FakeRecipeRepository : RecipeRepository {
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
    ) = Unit

    override suspend fun updateRecipe(
        recipeId: String,
        title: String,
        description: String?,
        category: String?,
        ingredients: List<Ingredient>,
    ) = Unit

    override suspend fun deleteRecipe(recipeId: String) = Unit
}

private class FakeShoppingRepository : ShoppingRepository {
    val addedNames = mutableListOf<String>()
    val addedNotes = mutableListOf<String?>()
    val addedSupermarkets = mutableListOf<Supermarket?>()
    val addedBrands = mutableListOf<String?>()

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
        addedNotes += notes
        addedSupermarkets += supermarket
        addedBrands += brand
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

private class FakeIngredientPrefsRepository : IngredientPrefsRepository {
    val prefsState = MutableStateFlow<Map<String, IngredientPref>>(emptyMap())
    override fun prefs(): Flow<Map<String, IngredientPref>> = prefsState
    override suspend fun setPref(ingredientId: String, supermarket: Supermarket?, brand: String?) = Unit
    override suspend fun clearPref(ingredientId: String) = Unit
}

private fun recipe(id: String, ingredients: List<Ingredient>) = Recipe(
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

private fun meal(id: String, date: LocalDate, slot: MealSlot, name: String) = Meal(
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
