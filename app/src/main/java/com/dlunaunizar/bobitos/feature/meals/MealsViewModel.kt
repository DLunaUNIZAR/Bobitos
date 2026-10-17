package com.dlunaunizar.bobitos.feature.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.failed
import com.dlunaunizar.bobitos.core.common.started
import com.dlunaunizar.bobitos.core.common.succeeded
import com.dlunaunizar.bobitos.core.common.toFailureKind
import com.dlunaunizar.bobitos.core.common.withEditorSaveTimeout
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import com.dlunaunizar.bobitos.data.repository.MealFailure
import com.dlunaunizar.bobitos.data.repository.MealRepository
import com.dlunaunizar.bobitos.data.repository.MealRepositoryException
import com.dlunaunizar.bobitos.data.repository.PantryRepository
import com.dlunaunizar.bobitos.data.repository.RecipeRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingCrossOff
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.feature.common.IngredientReviewChoice
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingData
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingSource
import com.dlunaunizar.bobitos.feature.common.applyIngredientReview
import com.dlunaunizar.bobitos.feature.common.buildIngredientReviewRows
import com.dlunaunizar.bobitos.feature.common.firstOrEmpty
import com.dlunaunizar.bobitos.feature.common.idsFlow
import com.dlunaunizar.bobitos.feature.common.pantryEntryFor
import com.dlunaunizar.bobitos.feature.common.pantryRestock
import com.dlunaunizar.bobitos.feature.common.withCurrentIds
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
                .catch { error -> mutableUiState.update { it.copy(members = UiState.Error(error.toFailureKind())) } }
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
            pantryRepository.idsFlow(spaceId)
                .catch { mutableUiState.update { state -> state.copy(pantryIds = null) } }
                .collect { ids -> mutableUiState.update { it.copy(pantryIds = ids) } }
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
        val trimmed = name.trim()
        // Un alta desde la hoja deja `created` (con «Deshacer») en lugar del aviso de comida añadida.
        runAction(null, editor = true) {
            CreatedItem(repository.addMeal(spaceId, date, slot, trimmed, participantIds, recipeId, cookId), trimmed)
        }
    }

    fun consumeCreated() = mutableUiState.update { it.copy(created = null) }

    // «Deshacer» el alta: borra justo esa comida con el borrado de siempre (sin aviso; si falla, sale el error).
    fun undoCreate(id: String) {
        val spaceId = observedSpaceId ?: return
        runAction(null, force = true) { repository.deleteMeal(spaceId, id) }
    }

    // «Deshacer» el borrado: la comida vuelve con su id y su estado, al espacio en que estaba (no al que se esté
    // viendo ahora). No es un guardado del editor ni lleva aviso propio; si falla, sale el error.
    fun restoreMeal(spaceId: String, meal: Meal) {
        runAction(null, force = true) { repository.restoreMeal(spaceId, meal) }
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

    // onDeleted se llama solo si el borrado termina bien: así el «Deshacer» no se ofrece para un borrado fallido.
    // Sin aviso propio: el snackbar de «Deshacer» ya confirma el borrado.
    fun deleteMeal(mealId: String, onDeleted: () -> Unit = {}) {
        val spaceId = observedSpaceId ?: return
        runAction(null, onSuccess = onDeleted) {
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
                // La lista y la despensa del servidor, a la vez. Sin la lista no se pueden buscar repetidos: si no se
                // puede leer, no se abre la revisión; si no se puede leer la despensa, la revisión sale sin ella.
                val (current, pantryIds) = runCatching {
                    pantryRepository.withCurrentIds(spaceId) { shoppingRepository.currentItems(spaceId) }
                }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrElse { error ->
                        showError(error.toUiMessage())
                        return@launch
                    }
                val rows = buildIngredientReviewRows(ingredients, current, shoppingData, pantryIds.orEmpty())
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
                    val (items, pantryIds) = pantryRepository.withCurrentIds(spaceId) {
                        runCatching { shoppingRepository.currentItems(spaceId) }
                            .onFailure { if (it is CancellationException) throw it }
                            .getOrNull()
                    }
                    cookedReview(meal.name, ingredients, items, pantryIds)
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

    // «Listo» en «Has cocinado …», en una sola escritura (ShoppingRepository.applyCooked): tacha lo marcado (si es un
    // ingrediente reconocido, entra en la despensa, como al marcar en la Compra), quita de la despensa lo que se ha
    // acabado y, si se pide, lo apunta a la compra. El diálogo sigue abierto hasta que se escribe: si falla, no se ha
    // escrito nada y conserva lo marcado.
    // Si la lista ha cambiado entretanto, basta con volver a pulsar «Listo» (toCookedUiMessage).
    fun confirmCooked(choice: CookedChoice) {
        val spaceId = observedSpaceId ?: return
        val writes = mutableUiState.value.cooked?.writesFor(choice) ?: return
        if (writes.isEmpty) {
            mutableUiState.update { it.copy(cooked = null, error = null) }
            return
        }
        runAction(
            MealUiMessage.CookedReviewApplied,
            toMessage = { error ->
                // Qué artículo ha cambiado, para nombrarlo en el diálogo (MealsUiState.cookedErrorItem).
                val itemName = (error as? ShoppingRepositoryException)?.itemName
                mutableUiState.update { it.copy(changedItemName = itemName) }
                // Si se ha pulsado «Cancelar» mientras guardaba, ya no hay «Listo» que volver a pulsar.
                error.toCookedUiMessage(dialogOpen = mutableUiState.value.cooked != null)
            },
        ) {
            val needsCatalog = writes.crossOff.isNotEmpty() || writes.restockNames.isNotEmpty()
            // Primero el catálogo (al momento si ya ha llegado; si no, hasta 5 s) y después la lista: «A la compra»
            // trabaja sobre la lista recién leída y no duplica lo que otro miembro apunte mientras se espera.
            val data = if (needsCatalog) ingredientData() else IngredientShoppingData()
            val restock = if (writes.restockNames.isEmpty()) {
                emptyList()
            } else {
                pantryRestock(writes.restockNamesIn(data), shoppingRepository.currentItems(spaceId), data).writes
            }
            shoppingRepository.applyCooked(
                spaceId,
                writes.crossOff.map { ShoppingCrossOff(it.id, pantryEntryFor(it.name, data)) },
                writes.finishedIds,
                restock,
            )
            mutableUiState.update { it.copy(cooked = null) }
        }
    }

    // «Cancelar»: el error de «Listo» es del diálogo y se va con él.
    fun dismissCooked() = mutableUiState.update { it.copy(cooked = null, error = null) }

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
                .catch { error -> mutableUiState.update { it.copy(meals = UiState.Error(error.toFailureKind())) } }
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

    private fun runAction(
        successNotice: MealUiMessage?,
        editor: Boolean = false,
        toMessage: (Throwable) -> MealUiMessage = Throwable::toUiMessage,
        force: Boolean = false,
        onSuccess: () -> Unit = {},
        // Si la acción devuelve un [CreatedItem], es un alta desde la hoja y queda en `created`.
        action: suspend () -> Any?,
    ) {
        if (mutableUiState.value.isSaving && !force) return
        mutableUiState.update {
            it.copy(isSaving = true, error = null, notice = null, editorSave = it.editorSave.started(editor))
        }
        viewModelScope.launch {
            runCatching { withEditorSaveTimeout(editor) { action() } }
                .onSuccess { result ->
                    mutableUiState.update {
                        it.copy(
                            isSaving = false,
                            notice = successNotice,
                            created = result as? CreatedItem,
                            editorSave = it.editorSave.succeeded(editor),
                        )
                    }
                    onSuccess()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    showError(toMessage(error), editor)
                }
        }
    }
}

private fun Throwable.toUiMessage(): MealUiMessage = when (this) {
    is MealRepositoryException -> failure.toUiMessage()
    is ShoppingRepositoryException -> failure.toUiMessage()
    is SaveTimeoutException -> MealUiMessage.SaveTimeout
    else -> MealUiMessage.UnexpectedError
}

// «Listo» en «Has cocinado …»: si un artículo que se vuelve a apuntar ha cambiado o se ha borrado, no se ha escrito
// nada. Con el diálogo abierto basta con volver a pulsar «Listo», que vuelve a leer la lista; si se ha cancelado
// mientras guardaba, solo se avisa de que no se ha escrito nada. Los demás errores, como en el resto de Comidas.
private fun Throwable.toCookedUiMessage(dialogOpen: Boolean): MealUiMessage {
    val failure = (this as? ShoppingRepositoryException)?.failure
    return when {
        failure != ShoppingFailure.ItemChanged && failure != ShoppingFailure.ItemNotFound -> toUiMessage()
        dialogOpen -> MealUiMessage.CookedShoppingChanged
        else -> MealUiMessage.CookedNotApplied
    }
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
    // La revisión de «Añadir a la compra» convierte ItemNotFound en ItemChanged (applySeenWrites); «Listo» al cocinar
    // elige su propio mensaje (toCookedUiMessage).
    ShoppingFailure.ItemChanged -> MealUiMessage.ShoppingChanged
    ShoppingFailure.ItemNotFound,
    ShoppingFailure.NameRequired,
    ShoppingFailure.NameTooLong,
    ShoppingFailure.QuantityTooLong,
    ShoppingFailure.NotesTooLong,
    ShoppingFailure.BrandTooLong,
    ShoppingFailure.Unknown,
    -> MealUiMessage.UnexpectedError
}
