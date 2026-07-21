package com.dlunaunizar.bobitos.feature.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.data.repository.MealFailure
import com.dlunaunizar.bobitos.data.repository.MealRepository
import com.dlunaunizar.bobitos.data.repository.MealRepositoryException
import com.dlunaunizar.bobitos.data.repository.RecipeRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MealsViewModel @Inject constructor(
    private val repository: MealRepository,
    private val spaces: SpaceRepository,
    private val recipeRepository: RecipeRepository,
    private val shoppingRepository: ShoppingRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MealsUiState())
    val uiState: StateFlow<MealsUiState> = mutableUiState.asStateFlow()

    private var observedSpaceId: String? = null
    private var observedWeekStart: LocalDate? = null
    private var mealsJob: Job? = null
    private var membersJob: Job? = null
    private var recipesJob: Job? = null

    fun observe(spaceId: String) {
        if (spaceId == observedSpaceId && mealsJob?.isActive == true) return
        observedSpaceId = spaceId
        observeWeek(spaceId)
        membersJob?.cancel()
        mutableUiState.update { it.copy(members = UiState.Loading) }
        membersJob = viewModelScope.launch {
            spaces.members(spaceId)
                .catch { error -> mutableUiState.update { it.copy(members = UiState.Error(error.message)) } }
                .collect { members -> mutableUiState.update { it.copy(members = UiState.Content(members)) } }
        }
        recipesJob?.cancel()
        recipesJob = viewModelScope.launch {
            combine(recipeRepository.myRecipes(), recipeRepository.globalRecipes()) { mine, global ->
                (mine + global).distinctBy { it.id }.sortedBy { it.title.lowercase() }
            }
                .catch { mutableUiState.update { it.copy(recipes = emptyList()) } }
                .collect { recipes -> mutableUiState.update { it.copy(recipes = recipes) } }
        }
    }

    fun stopObserving() {
        mealsJob?.cancel()
        membersJob?.cancel()
        recipesJob?.cancel()
        mealsJob = null
        membersJob = null
        recipesJob = null
        observedSpaceId = null
        observedWeekStart = null
    }

    fun previousWeek() = goToDate(mutableUiState.value.focusedDate.minusWeeks(1))

    fun nextWeek() = goToDate(mutableUiState.value.focusedDate.plusWeeks(1))

    fun selectDay(date: LocalDate) = goToDate(date)

    fun addMeal(date: LocalDate, slot: MealSlot, name: String, participantIds: List<String>, recipeId: String?) {
        val spaceId = observedSpaceId ?: return
        if (!validate(name)) return
        runAction(MealUiMessage.MealAdded) {
            repository.addMeal(spaceId, date, slot, name.trim(), participantIds, recipeId)
        }
    }

    fun updateMeal(
        mealId: String,
        date: LocalDate,
        slot: MealSlot,
        name: String,
        participantIds: List<String>,
        recipeId: String?,
    ) {
        val spaceId = observedSpaceId ?: return
        if (!validate(name)) return
        runAction(MealUiMessage.MealUpdated) {
            repository.updateMeal(spaceId, mealId, date, slot, name.trim(), participantIds, recipeId)
        }
    }

    fun deleteMeal(mealId: String) {
        val spaceId = observedSpaceId ?: return
        runAction(MealUiMessage.MealDeleted) {
            repository.deleteMeal(spaceId, mealId)
        }
    }

    // Vuelca los ingredientes de la receta enlazada a la comida a la lista de la Compra del espacio.
    fun addIngredientsToShopping(meal: Meal) {
        val spaceId = observedSpaceId ?: return
        val ingredients = meal.recipeId
            ?.let { id -> mutableUiState.value.recipes.firstOrNull { it.id == id } }
            ?.ingredients
            .orEmpty()
        if (ingredients.isEmpty()) return
        runAction(MealUiMessage.IngredientsAddedToShopping) {
            ingredients.forEach { ingredient ->
                shoppingRepository.addItem(
                    spaceId = spaceId,
                    name = ingredient.name,
                    quantity = ingredient.quantity,
                    notes = ingredient.unit,
                    supermarket = null,
                    brand = null,
                )
            }
        }
    }

    fun clearFeedback() {
        mutableUiState.update { it.copy(error = null, notice = null) }
    }

    private fun goToDate(date: LocalDate) {
        if (date == mutableUiState.value.focusedDate) return
        mutableUiState.update { it.copy(focusedDate = date) }
        val spaceId = observedSpaceId ?: return
        if (mutableUiState.value.weekStart != observedWeekStart) observeWeek(spaceId)
    }

    private fun observeWeek(spaceId: String) {
        mealsJob?.cancel()
        val weekStart = mutableUiState.value.weekStart
        observedWeekStart = weekStart
        mutableUiState.update { it.copy(meals = UiState.Loading) }
        mealsJob = viewModelScope.launch {
            repository.meals(spaceId, weekStart, weekStart.plusWeeks(1))
                .catch { error -> mutableUiState.update { it.copy(meals = UiState.Error(error.message)) } }
                .collect { meals -> mutableUiState.update { it.copy(meals = UiState.Content(meals)) } }
        }
    }

    private fun validate(name: String): Boolean {
        val error = MealsValidation.validate(name) ?: return true
        showError(error)
        return false
    }

    private fun showError(message: MealUiMessage) {
        mutableUiState.update { it.copy(isSaving = false, error = message, notice = null) }
    }

    private fun runAction(successNotice: MealUiMessage, action: suspend () -> Unit) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update { it.copy(isSaving = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                action()
                mutableUiState.update { it.copy(isSaving = false, notice = successNotice) }
            } catch (error: Throwable) {
                showError(error.toUiMessage())
            }
        }
    }
}

private fun Throwable.toUiMessage(): MealUiMessage = when (this) {
    is MealRepositoryException -> failure.toUiMessage()
    is ShoppingRepositoryException -> failure.toUiMessage()
    else -> MealUiMessage.UnexpectedError
}

private fun MealFailure.toUiMessage(): MealUiMessage = when (this) {
    MealFailure.NameRequired -> MealUiMessage.NameRequired
    MealFailure.NameTooLong -> MealUiMessage.NameTooLong
    MealFailure.InvalidParticipants -> MealUiMessage.InvalidParticipants
    MealFailure.NotAuthenticated -> MealUiMessage.NotAuthenticated
    MealFailure.EmailNotVerified -> MealUiMessage.EmailNotVerified
    MealFailure.SpaceNotFound -> MealUiMessage.SpaceNotFound
    MealFailure.MealNotFound -> MealUiMessage.MealNotFound
    MealFailure.PermissionDenied -> MealUiMessage.PermissionDenied
    MealFailure.Network -> MealUiMessage.NetworkError
    MealFailure.Unknown -> MealUiMessage.UnexpectedError
}

private fun ShoppingFailure.toUiMessage(): MealUiMessage = when (this) {
    ShoppingFailure.NotAuthenticated -> MealUiMessage.NotAuthenticated
    ShoppingFailure.EmailNotVerified -> MealUiMessage.EmailNotVerified
    ShoppingFailure.SpaceNotFound -> MealUiMessage.SpaceNotFound
    ShoppingFailure.PermissionDenied -> MealUiMessage.PermissionDenied
    ShoppingFailure.Network -> MealUiMessage.NetworkError
    ShoppingFailure.NameRequired,
    ShoppingFailure.NameTooLong,
    ShoppingFailure.QuantityTooLong,
    ShoppingFailure.NotesTooLong,
    ShoppingFailure.BrandTooLong,
    ShoppingFailure.ItemNotFound,
    ShoppingFailure.Unknown,
    -> MealUiMessage.UnexpectedError
}
