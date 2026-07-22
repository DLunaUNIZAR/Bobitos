package com.dlunaunizar.bobitos.feature.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.slug
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
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
import kotlinx.coroutines.flow.first
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
    private val ingredientPrefsRepository: IngredientPrefsRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MealsUiState())
    val uiState: StateFlow<MealsUiState> = mutableUiState.asStateFlow()

    private var observedSpaceId: String? = null
    private var observedWeekStart: LocalDate? = null
    private var mealsJob: Job? = null
    private var membersJob: Job? = null
    private var recipesJob: Job? = null
    private var prefsJob: Job? = null

    // Preferencias del usuario (super/marca por defecto por ingrediente), para prerrellenar la compra.
    private var ingredientPrefs: Map<String, IngredientPref> = emptyMap()

    fun observe(spaceId: String) {
        observePrefs()
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

    private fun observePrefs() {
        if (prefsJob?.isActive == true) return
        prefsJob = viewModelScope.launch {
            ingredientPrefsRepository.prefs()
                .catch { ingredientPrefs = emptyMap() }
                .collect { prefs -> ingredientPrefs = prefs }
        }
    }

    fun stopObserving() {
        mealsJob?.cancel()
        membersJob?.cancel()
        recipesJob?.cancel()
        prefsJob?.cancel()
        mealsJob = null
        membersJob = null
        recipesJob = null
        prefsJob = null
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
        // Sin notice: el feedback del borrado (con «Deshacer») lo da el Snackbar de la pantalla.
        runAction(null) {
            repository.deleteMeal(spaceId, mealId)
        }
    }

    // Duplica las comidas del día enfocado en [targetDate].
    fun duplicateDay(targetDate: LocalDate) {
        val spaceId = observedSpaceId ?: return
        val meals = currentMeals().filter { it.date == mutableUiState.value.focusedDate }
        if (meals.isEmpty()) return
        runAction(MealUiMessage.MealsDuplicated) {
            meals.forEach { repository.addMeal(spaceId, targetDate, it.slot, it.name, it.participantIds, it.recipeId) }
        }
    }

    // Duplica las comidas de la semana enfocada en la semana siguiente.
    fun duplicateWeekToNext() {
        val spaceId = observedSpaceId ?: return
        val meals = currentMeals()
        if (meals.isEmpty()) return
        runAction(MealUiMessage.MealsDuplicated) {
            meals.forEach {
                repository.addMeal(spaceId, it.date.plusWeeks(1), it.slot, it.name, it.participantIds, it.recipeId)
            }
        }
    }

    // Abre la revisión para volcar a la Compra los ingredientes de una comida / del día / de la semana.
    fun addIngredientsToShopping(meal: Meal) = prepareIngredientReview(listOf(meal))

    fun addDayIngredientsToShopping() =
        prepareIngredientReview(currentMeals().filter { it.date == mutableUiState.value.focusedDate })

    fun addWeekIngredientsToShopping() = prepareIngredientReview(currentMeals())

    private fun prepareIngredientReview(meals: List<Meal>) {
        val spaceId = observedSpaceId ?: return
        val recipesById = mutableUiState.value.recipes.associateBy { it.id }
        // Dedup por nombre (conserva el primero); la cantidad final la decide el usuario en la revisión.
        val ingredients = meals.mapNotNull(Meal::recipeId)
            .mapNotNull(recipesById::get)
            .flatMap { it.ingredients.orEmpty() }
            .distinctBy { it.name.trim().lowercase() }
        if (ingredients.isEmpty()) return
        viewModelScope.launch {
            val current = runCatching { shoppingRepository.items(spaceId).first() }.getOrDefault(emptyList())
            val byName = current.associateBy { it.name.trim().lowercase() }
            val rows = ingredients.map { ingredient ->
                IngredientReviewRow(
                    name = ingredient.name,
                    unit = ingredient.unit,
                    recipeQuantity = ingredient.quantity,
                    existing = byName[ingredient.name.trim().lowercase()],
                )
            }
            mutableUiState.update { it.copy(ingredientReview = rows) }
        }
    }

    fun dismissIngredientReview() = mutableUiState.update { it.copy(ingredientReview = null) }

    // Aplica la revisión: actualiza la cantidad de los que ya existen y añade el resto.
    fun confirmIngredientReview(finalQuantities: List<String?>) {
        val spaceId = observedSpaceId ?: return
        val rows = mutableUiState.value.ingredientReview ?: return
        mutableUiState.update { it.copy(ingredientReview = null) }
        runAction(MealUiMessage.IngredientsAddedToShopping) {
            rows.forEachIndexed { index, row ->
                val quantity = finalQuantities.getOrNull(index)?.trim()?.takeIf(String::isNotEmpty)
                val existing = row.existing
                if (existing != null) {
                    shoppingRepository.updateItem(
                        spaceId,
                        existing.id,
                        existing.name,
                        quantity,
                        existing.notes,
                        existing.supermarket,
                        existing.brand,
                    )
                } else {
                    val pref = ingredientPrefs[slug(row.name)]
                    shoppingRepository.addItem(spaceId, row.name, quantity, row.unit, pref?.supermarket, pref?.brand)
                }
            }
        }
    }

    private fun currentMeals(): List<Meal> = (mutableUiState.value.meals as? UiState.Content)?.value.orEmpty()

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

    private fun runAction(successNotice: MealUiMessage?, action: suspend () -> Unit) {
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
