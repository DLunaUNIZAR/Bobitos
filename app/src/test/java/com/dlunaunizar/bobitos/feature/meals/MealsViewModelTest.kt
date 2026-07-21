package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.data.repository.MealFailure
import com.dlunaunizar.bobitos.data.repository.MealRepository
import com.dlunaunizar.bobitos.data.repository.MealRepositoryException
import com.dlunaunizar.bobitos.data.repository.RecipeRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
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
    private val viewModel = MealsViewModel(mealRepository, spaceRepository, recipeRepository)

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
    override fun globalRecipes(): Flow<List<Recipe>> = MutableStateFlow(emptyList())
    override fun myRecipes(): Flow<List<Recipe>> = MutableStateFlow(emptyList())
    override suspend fun createRecipe(
        visibility: RecipeVisibility,
        title: String,
        description: String?,
        category: String?,
        sourceRecipeId: String?,
    ) = Unit

    override suspend fun updateRecipe(recipeId: String, title: String, description: String?, category: String?) = Unit

    override suspend fun deleteRecipe(recipeId: String) = Unit
}

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
