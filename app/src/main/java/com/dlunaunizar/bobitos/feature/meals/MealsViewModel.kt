package com.dlunaunizar.bobitos.feature.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.failed
import com.dlunaunizar.bobitos.core.common.started
import com.dlunaunizar.bobitos.core.common.succeeded
import com.dlunaunizar.bobitos.core.common.withEditorSaveTimeout
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.core.model.PantryItem
import com.dlunaunizar.bobitos.data.repository.MealFailure
import com.dlunaunizar.bobitos.data.repository.MealRepository
import com.dlunaunizar.bobitos.data.repository.MealRepositoryException
import com.dlunaunizar.bobitos.data.repository.PantryFailure
import com.dlunaunizar.bobitos.data.repository.PantryRepository
import com.dlunaunizar.bobitos.data.repository.PantryRepositoryException
import com.dlunaunizar.bobitos.data.repository.RecipeRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.feature.common.IngredientReviewChoice
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingData
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingSource
import com.dlunaunizar.bobitos.feature.common.applyIngredientReview
import com.dlunaunizar.bobitos.feature.common.buildIngredientReviewRows
import com.dlunaunizar.bobitos.feature.common.currentIdsOrNull
import com.dlunaunizar.bobitos.feature.common.firstOrEmpty
import com.dlunaunizar.bobitos.feature.common.pantryEntryFor
import com.dlunaunizar.bobitos.feature.common.restockFromPantry
import com.dlunaunizar.bobitos.feature.common.writesNothing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    private val ingredientShoppingSource: IngredientShoppingSource,
    private val pantryRepository: PantryRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MealsUiState())
    val uiState: StateFlow<MealsUiState> = mutableUiState.asStateFlow()

    private var observedSpaceId: String? = null
    private var observedWeekStart: LocalDate? = null
    private var mealsJob: Job? = null
    private var membersJob: Job? = null
    private var recipesJob: Job? = null
    private var pantryJob: Job? = null
    private var prefsJob: Job? = null

    // «Dónde lo compro» del usuario (tiendas por ingrediente), para prerrellenar la compra.
    private var shoppingData: IngredientShoppingData = IngredientShoppingData()

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
        observePantry(spaceId)
    }

    // La despensa del espacio, para el chip «Con lo que hay» del selector de receta; si no se puede leer, sin chip.
    private fun observePantry(spaceId: String) {
        pantryJob?.cancel()
        mutableUiState.update { it.copy(pantryIds = null) }
        pantryJob = viewModelScope.launch {
            pantryRepository.items(spaceId)
                .catch { mutableUiState.update { state -> state.copy(pantryIds = null) } }
                .collect { items ->
                    mutableUiState.update { it.copy(pantryIds = items.mapTo(HashSet(), PantryItem::id)) }
                }
        }
    }

    private fun observePrefs() {
        if (prefsJob?.isActive == true) return
        prefsJob = viewModelScope.launch {
            ingredientShoppingSource.data()
                .catch { shoppingData = IngredientShoppingData() }
                .collect { data -> shoppingData = data }
        }
    }

    fun stopObserving() {
        mealsJob?.cancel()
        membersJob?.cancel()
        recipesJob?.cancel()
        pantryJob?.cancel()
        prefsJob?.cancel()
        mealsJob = null
        membersJob = null
        recipesJob = null
        pantryJob = null
        prefsJob = null
        observedSpaceId = null
        observedWeekStart = null
    }

    fun previousWeek() = goToDate(mutableUiState.value.focusedDate.minusWeeks(1))

    fun nextWeek() = goToDate(mutableUiState.value.focusedDate.plusWeeks(1))

    fun selectDay(date: LocalDate) = goToDate(date)

    fun addMeal(
        date: LocalDate,
        slot: MealSlot,
        name: String,
        participantIds: List<String>,
        recipeId: String?,
        cookId: String?,
    ) {
        val spaceId = observedSpaceId ?: return
        if (!validate(name)) return
        runAction(MealUiMessage.MealAdded, editor = true) {
            repository.addMeal(spaceId, date, slot, name.trim(), participantIds, recipeId, cookId)
        }
    }

    // Recrea una comida borrada («Deshacer»): no es un guardado del editor, no mueve su estado.
    fun restoreMeal(meal: Meal) {
        val spaceId = observedSpaceId ?: return
        runAction(MealUiMessage.MealAdded) {
            repository.addMeal(
                spaceId,
                meal.date,
                meal.slot,
                meal.name,
                meal.participantIds,
                meal.recipeId,
                meal.cookId,
            )
        }
    }

    fun consumeEditorSave() = mutableUiState.update { it.copy(editorSave = EditorSaveStatus.IDLE) }

    fun updateMeal(
        mealId: String,
        date: LocalDate,
        slot: MealSlot,
        name: String,
        participantIds: List<String>,
        recipeId: String?,
        cookId: String?,
    ) {
        val spaceId = observedSpaceId ?: return
        if (!validate(name)) return
        runAction(MealUiMessage.MealUpdated, editor = true) {
            repository.updateMeal(spaceId, mealId, date, slot, name.trim(), participantIds, recipeId, cookId)
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
            meals.forEach {
                repository.addMeal(spaceId, targetDate, it.slot, it.name, it.participantIds, it.recipeId, it.cookId)
            }
        }
    }

    // Duplica las comidas de la semana enfocada en la semana siguiente.
    fun duplicateWeekToNext() {
        val spaceId = observedSpaceId ?: return
        val meals = currentMeals()
        if (meals.isEmpty()) return
        runAction(MealUiMessage.MealsDuplicated) {
            meals.forEach {
                repository.addMeal(
                    spaceId,
                    it.date.plusWeeks(1),
                    it.slot,
                    it.name,
                    it.participantIds,
                    it.recipeId,
                    it.cookId,
                )
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
        val ingredients = meals.mapNotNull(Meal::recipeId)
            .mapNotNull(recipesById::get)
            .flatMap { it.ingredients.orEmpty() }
        if (ingredients.isEmpty()) return
        // La lectura del servidor puede tardar: mientras espera, isSaving evita lecturas en paralelo.
        if (mutableUiState.value.isSaving) return
        mutableUiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                // Sin la lista no se pueden buscar repetidos: si no se puede leer, no se abre la revisión.
                val current = runCatching { shoppingRepository.currentItems(spaceId) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrElse { error ->
                        showError(error.toUiMessage())
                        return@launch
                    }
                // La despensa del servidor; si no se puede leer, la revisión sale sin ella.
                val pantryIds = pantryRepository.currentIdsOrNull(spaceId).orEmpty()
                val rows = buildIngredientReviewRows(ingredients, current, shoppingData, pantryIds)
                mutableUiState.update { it.copy(ingredientReview = rows) }
            } finally {
                mutableUiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun dismissIngredientReview() = mutableUiState.update { it.copy(ingredientReview = null) }

    // Marca la comida como cocinada y, si su receta tiene ingredientes pendientes en la compra o que hay en la
    // despensa, abre «Has cocinado …» (ver cookedReview). Las dos se leen del servidor; si una no se puede leer, su
    // sección no sale.
    fun markCooked(meal: Meal) {
        val spaceId = observedSpaceId ?: return
        if (mutableUiState.value.isSaving) return
        val ingredients = mutableUiState.value.recipes.firstOrNull { it.id == meal.recipeId }?.ingredients.orEmpty()
        mutableUiState.update { it.copy(isSaving = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                repository.setCooked(spaceId, meal.id, true)
                val review = if (ingredients.isEmpty()) {
                    null
                } else {
                    val items = runCatching { shoppingRepository.currentItems(spaceId) }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
                    cookedReview(meal.name, ingredients, items, pantryRepository.currentIdsOrNull(spaceId))
                }
                mutableUiState.update { it.copy(isSaving = false, notice = MealUiMessage.MealCooked, cooked = review) }
            } catch (error: Throwable) {
                showError(error.toUiMessage())
            }
        }
    }

    fun unmarkCooked(meal: Meal) {
        val spaceId = observedSpaceId ?: return
        runAction(null) { repository.setCooked(spaceId, meal.id, false) }
    }

    // «Listo» en «Has cocinado …»: tacha lo marcado (si es un ingrediente reconocido, entra en la despensa, como al
    // marcar en la Compra), quita de la despensa lo que se ha acabado y, si se pide, lo apunta a la compra.
    fun confirmCooked(choice: CookedChoice) {
        val spaceId = observedSpaceId ?: return
        val writes = mutableUiState.value.cooked?.writesFor(choice) ?: return
        mutableUiState.update { it.copy(cooked = null) }
        if (writes.isEmpty) return
        runAction(MealUiMessage.CookedReviewApplied) {
            val needsCatalog = writes.crossOff.isNotEmpty() || writes.restockNames.isNotEmpty()
            val data = if (needsCatalog) ingredientData() else IngredientShoppingData()
            writes.crossOff.forEach { item ->
                shoppingRepository.setPurchased(spaceId, item.id, true, pantryEntryFor(item.name, data))
            }
            if (writes.finishedIds.isNotEmpty()) pantryRepository.remove(spaceId, writes.finishedIds)
            if (writes.restockNames.isNotEmpty()) {
                restockFromPantry(spaceId, writes.restockNames, shoppingRepository, data)
            }
        }
    }

    fun dismissCooked() = mutableUiState.update { it.copy(cooked = null) }

    // Catálogo para reconocer ingredientes: el que ya escucha la pantalla; si aún no ha llegado, se espera 5 s como máximo
    // y, si no llega, se sigue sin él (lo tachado no entra en la despensa; lo apuntado va sin marca ni tienda).
    private suspend fun ingredientData(): IngredientShoppingData =
        shoppingData.takeIf { it.ingredients.isNotEmpty() } ?: ingredientShoppingSource.firstOrEmpty()

    // Aplica la revisión: omite, suma, vuelve a apuntar o añade cada fila (ver applyIngredientReview).
    fun confirmIngredientReview(choices: List<IngredientReviewChoice>) {
        val spaceId = observedSpaceId ?: return
        val rows = mutableUiState.value.ingredientReview ?: return
        if (choices.writesNothing()) {
            mutableUiState.update {
                it.copy(ingredientReview = null, error = null, notice = MealUiMessage.NothingToAddToShopping)
            }
            return
        }
        mutableUiState.update { it.copy(ingredientReview = null) }
        runAction(MealUiMessage.IngredientsAddedToShopping) {
            applyIngredientReview(spaceId, rows, choices, shoppingRepository)
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
        showError(error, editor = true)
        return false
    }

    private fun showError(message: MealUiMessage, editor: Boolean = false) {
        mutableUiState.update {
            it.copy(isSaving = false, error = message, notice = null, editorSave = it.editorSave.failed(editor))
        }
    }

    private fun runAction(successNotice: MealUiMessage?, editor: Boolean = false, action: suspend () -> Unit) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update {
            it.copy(isSaving = true, error = null, notice = null, editorSave = it.editorSave.started(editor))
        }
        viewModelScope.launch {
            runCatching { withEditorSaveTimeout(editor) { action() } }
                .onSuccess {
                    mutableUiState.update {
                        it.copy(isSaving = false, notice = successNotice, editorSave = it.editorSave.succeeded(editor))
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    showError(error.toUiMessage(), editor)
                }
        }
    }
}

private fun Throwable.toUiMessage(): MealUiMessage = when (this) {
    is MealRepositoryException -> failure.toUiMessage()
    is ShoppingRepositoryException -> failure.toUiMessage()
    is PantryRepositoryException -> failure.toUiMessage()
    is SaveTimeoutException -> MealUiMessage.SaveTimeout
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
    ShoppingFailure.ItemChanged -> MealUiMessage.ShoppingChanged
    ShoppingFailure.NameRequired,
    ShoppingFailure.NameTooLong,
    ShoppingFailure.QuantityTooLong,
    ShoppingFailure.NotesTooLong,
    ShoppingFailure.BrandTooLong,
    ShoppingFailure.ItemNotFound,
    ShoppingFailure.Unknown,
    -> MealUiMessage.UnexpectedError
}

private fun PantryFailure.toUiMessage(): MealUiMessage = when (this) {
    PantryFailure.NotAuthenticated -> MealUiMessage.NotAuthenticated
    PantryFailure.EmailNotVerified -> MealUiMessage.EmailNotVerified
    PantryFailure.SpaceNotFound -> MealUiMessage.SpaceNotFound
    PantryFailure.PermissionDenied -> MealUiMessage.PermissionDenied
    PantryFailure.Network -> MealUiMessage.NetworkError
    PantryFailure.NameRequired,
    PantryFailure.NameTooLong,
    PantryFailure.Unknown,
    -> MealUiMessage.UnexpectedError
}
