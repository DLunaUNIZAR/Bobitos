package com.dlunaunizar.bobitos.feature.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.SaveFeedbackControls
import com.dlunaunizar.bobitos.core.common.SaveRunner
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.toFailureKind
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.core.model.cleanRecipeSteps
import com.dlunaunizar.bobitos.data.recipeimport.ImportFailure
import com.dlunaunizar.bobitos.data.recipeimport.RecipeImportException
import com.dlunaunizar.bobitos.data.recipeimport.RecipeImporter
import com.dlunaunizar.bobitos.data.repository.PantryRepository
import com.dlunaunizar.bobitos.data.repository.RecipeFailure
import com.dlunaunizar.bobitos.data.repository.RecipeRepository
import com.dlunaunizar.bobitos.data.repository.RecipeRepositoryException
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.feature.common.IngredientReviewChoice
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingData
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingSource
import com.dlunaunizar.bobitos.feature.common.applyIngredientReview
import com.dlunaunizar.bobitos.feature.common.buildIngredientReviewRows
import com.dlunaunizar.bobitos.feature.common.idsFlow
import com.dlunaunizar.bobitos.feature.common.withCurrentIds
import com.dlunaunizar.bobitos.feature.common.writesNothing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val repository: RecipeRepository,
    private val importer: RecipeImporter,
    private val shoppingRepository: ShoppingRepository,
    private val ingredientShoppingSource: IngredientShoppingSource,
    private val pantryRepository: PantryRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(RecipesUiState())
    val uiState: StateFlow<RecipesUiState> = mutableUiState.asStateFlow()
    private val saver =
        SaveRunner(viewModelScope, mutableUiState, RecipesUiState::save, { copy(save = it) }, Throwable::toUiMessage)
    val feedback: SaveFeedbackControls get() = saver

    private var globalJob: Job? = null
    private var mineJob: Job? = null
    private var prefsJob: Job? = null
    private var observing = false
    private var pantryJob: Job? = null
    private var pantrySpaceId: String? = null

    // «Dónde lo compro» del usuario (tiendas por ingrediente), para prerrellenar al añadir a la compra.
    private var shoppingData: IngredientShoppingData = IngredientShoppingData()

    fun observe() {
        if (observing) return
        observing = true
        mutableUiState.update { it.copy(isAdmin = repository.isCurrentUserRecipeAdmin()) }
        prefsJob = viewModelScope.launch {
            ingredientShoppingSource.data()
                .catch { shoppingData = IngredientShoppingData() }
                .collect { data -> shoppingData = data }
        }
        globalJob = viewModelScope.launch {
            repository.globalRecipes()
                .catch { error -> mutableUiState.update { it.copy(global = UiState.Error(error.toFailureKind())) } }
                .collect { recipes -> mutableUiState.update { it.copy(global = UiState.Content(recipes)) } }
        }
        mineJob = viewModelScope.launch {
            repository.myRecipes()
                .catch { error -> mutableUiState.update { it.copy(mine = UiState.Error(error.toFailureKind())) } }
                // «Mis recetas» = solo privadas; las GLOBAL (también las del admin) llegan por `global`.
                .collect { list -> mutableUiState.update { it.copy(mine = UiState.Content(list.personalOnly())) } }
        }
    }

    // Reintento tras un fallo de carga: reinicia solo las dos listas de recetas.
    fun retry() {
        prefsJob?.cancel()
        globalJob?.cancel()
        mineJob?.cancel()
        observing = false
        mutableUiState.update { it.copy(global = UiState.Loading, mine = UiState.Loading) }
        observe()
    }

    /**
     * Escucha la despensa de [spaceId] para «Con lo que hay»; con null (sin espacio) la suelta. Volver a observar el
     * mismo espacio (rotación, volver al Recetario) reinicia el listener conservando los ids hasta la instantánea nueva;
     * pasar a otro espacio (o a ninguno) los borra y apaga el chip, para que no quede un filtro activo y oculto.
     */
    fun observePantry(spaceId: String?) {
        if (spaceId == pantrySpaceId && pantryJob?.isActive == true) return
        pantryJob?.cancel()
        pantryJob = null
        val previous = pantrySpaceId
        pantrySpaceId = spaceId
        if (spaceId != previous) {
            // De ningún espacio al primero no se apaga: «¿Qué cocino?» activa el chip justo después de observar.
            val resetFilter = spaceId == null || previous != null
            mutableUiState.update {
                it.copy(pantryIds = null, pantryOnly = if (resetFilter) false else it.pantryOnly)
            }
        }
        if (spaceId == null) return
        pantryJob = viewModelScope.launch {
            pantryRepository.idsFlow(spaceId)
                // Si no se puede leer, el chip no sale.
                .catch { mutableUiState.update { state -> state.copy(pantryIds = null) } }
                .collect { ids -> mutableUiState.update { it.copy(pantryIds = ids) } }
        }
    }

    fun setPantryOnly(value: Boolean) = mutableUiState.update { it.copy(pantryOnly = value) }

    fun stopObserving() {
        pantryJob?.cancel()
        pantryJob = null
        globalJob?.cancel()
        mineJob?.cancel()
        prefsJob?.cancel()
        globalJob = null
        mineJob = null
        prefsJob = null
        observing = false
    }

    /** Abre la revisión para volcar los ingredientes de [recipe] a la lista de la compra de [spaceId]. */
    fun addToShopping(spaceId: String, recipe: Recipe) {
        val ingredients = recipe.ingredients.orEmpty()
        if (ingredients.isEmpty()) {
            saver.showError(RecipeUiMessage.NoIngredients)
            return
        }
        // La lectura del servidor puede tardar: mientras espera, «en curso» evita lecturas en paralelo. La lista y la
        // despensa del servidor, a la vez. Sin la lista no se pueden buscar repetidos: si no se puede leer, no se abre
        // la revisión; si no se puede leer la despensa, la revisión sale sin ella.
        saver.run(onSuccess = { rows -> copy(ingredientReview = rows) }) {
            val (current, pantryIds) =
                pantryRepository.withCurrentIds(spaceId) { shoppingRepository.currentItems(spaceId) }
            buildIngredientReviewRows(ingredients, current, shoppingData, pantryIds.orEmpty())
        }
    }

    fun confirmShoppingReview(spaceId: String, choices: List<IngredientReviewChoice>) {
        val rows = mutableUiState.value.ingredientReview ?: return
        if (choices.writesNothing()) {
            mutableUiState.update { it.copy(ingredientReview = null) }
            saver.showNotice(RecipeUiMessage.NothingToAddToShopping)
            return
        }
        mutableUiState.update { it.copy(ingredientReview = null) }
        saver.run(notice = RecipeUiMessage.AddedToShopping) {
            applyIngredientReview(spaceId, rows, choices, shoppingRepository)
        }
    }

    fun dismissShoppingReview() {
        mutableUiState.update { it.copy(ingredientReview = null) }
    }

    fun setQuery(query: String) {
        mutableUiState.update { it.copy(query = query) }
    }

    fun createRecipe(
        visibility: RecipeVisibility,
        title: String,
        description: String?,
        category: String?,
        ingredients: List<Ingredient> = emptyList(),
        sourceUrl: String? = null,
        steps: List<String> = emptyList(),
        servings: Int? = null,
        totalMinutes: Int? = null,
    ) {
        if (!validate(title, description, category, steps, servings, totalMinutes)) return
        val trimmed = title.trim()
        // Un alta desde la hoja (crear o importar) deja `created` (con «Deshacer») en lugar del aviso de guardado.
        saver.create(editor = true) {
            val id = repository.createRecipe(
                visibility = safeVisibility(visibility),
                title = trimmed,
                description = description.normalized(),
                category = category.normalized(),
                sourceRecipeId = null,
                ingredients = ingredients,
                sourceUrl = sourceUrl,
                steps = cleanRecipeSteps(steps),
                servings = servings,
                totalMinutes = totalMinutes,
            )
            CreatedItem(id, trimmed)
        }
    }

    /** Suelta el error de importación pendiente; el de otra acción sigue esperando. */
    fun consumeImportError() {
        saver.consumeErrorWhere { it.isImport }
    }

    /** Descarga la receta de [url] (schema.org/Recipe) y, si tiene éxito, la deja en `importDraft`. */
    fun importFromUrl(url: String) {
        if (mutableUiState.value.isImporting) return
        consumeImportError()
        mutableUiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            try {
                val draft = importer.import(url)
                mutableUiState.update { it.copy(isImporting = false, importDraft = draft) }
            } catch (error: RecipeImportException) {
                mutableUiState.update { it.copy(isImporting = false) }
                saver.showError(error.failure.toUiMessage())
            }
        }
    }

    /** La pantalla lo llama tras abrir el editor con el borrador importado. */
    fun consumeImportDraft() {
        mutableUiState.update { it.copy(importDraft = null) }
    }

    fun updateRecipe(
        recipeId: String,
        title: String,
        description: String?,
        category: String?,
        ingredients: List<Ingredient> = emptyList(),
        steps: List<String> = emptyList(),
        servings: Int? = null,
        totalMinutes: Int? = null,
    ) {
        if (!validate(title, description, category, steps, servings, totalMinutes)) return
        saver.run(notice = RecipeUiMessage.RecipeSaved, editor = true) {
            repository.updateRecipe(
                recipeId = recipeId,
                title = title.trim(),
                description = description.normalized(),
                category = category.normalized(),
                ingredients = ingredients,
                steps = cleanRecipeSteps(steps),
                servings = servings,
                totalMinutes = totalMinutes,
            )
        }
    }

    /** Guarda el borrador del editor: crea una receta si [recipeId] es null y, si no, edita esa. */
    internal fun saveDraft(recipeId: String?, draft: RecipeDraft) {
        val ingredients = draft.ingredients.toIngredients()
        val servings = draft.servings.toRecipeCount()
        val totalMinutes = draft.totalMinutes.toRecipeCount()
        if (recipeId == null) {
            createRecipe(
                visibility = if (draft.global) RecipeVisibility.GLOBAL else RecipeVisibility.PRIVATE,
                title = draft.title,
                description = draft.description,
                category = draft.category,
                ingredients = ingredients,
                sourceUrl = draft.sourceUrl,
                steps = draft.steps,
                servings = servings,
                totalMinutes = totalMinutes,
            )
        } else {
            updateRecipe(
                recipeId = recipeId,
                title = draft.title,
                description = draft.description,
                category = draft.category,
                ingredients = ingredients,
                steps = draft.steps,
                servings = servings,
                totalMinutes = totalMinutes,
            )
        }
    }

    // Recrea la receta borrada («Deshacer») tal como era, con su mismo id y su visibilidad: el importador del catálogo
    // no duplica una común recuperada y las comidas que la enlazaban la vuelven a encontrar. Si quien deshace ya no
    // puede publicar comunes, las reglas lo rechazan y sale el error (nunca se guarda como privada con el id del
    // catálogo). No es un guardado del editor, no mueve su estado.
    fun restoreRecipe(recipe: Recipe) = saver.run(force = true) { repository.restoreRecipe(recipe) }

    // Sin notice: el «Deshacer» lo ofrece la pantalla cuando el servidor confirma el borrado (deletedRecipe); si
    // falla, sale el error y no se ofrece.
    fun deleteRecipe(recipe: Recipe) {
        saver.run(onSuccess = { copy(deletedRecipe = recipe) }) { repository.deleteRecipe(recipe.id) }
    }

    fun consumeDeletedRecipe() = mutableUiState.update { it.copy(deletedRecipe = null) }

    fun fork(source: Recipe) {
        // «Guardar como mía» deja `created` con la copia (su id, no el de la original) en lugar del aviso de copia.
        saver.create {
            val id = repository.createRecipe(
                visibility = RecipeVisibility.PRIVATE,
                title = source.title,
                description = source.description,
                category = source.category,
                sourceRecipeId = source.id,
                ingredients = source.ingredients.orEmpty(),
                steps = source.steps.orEmpty(),
                servings = source.servings,
                totalMinutes = source.totalMinutes,
            )
            CreatedItem(id, source.title)
        }
    }

    // «Deshacer» el alta: borra justo esa receta con el borrado de siempre, sin ofrecer otro «Deshacer» (sin aviso; si
    // falla, sale el error).
    fun undoCreate(id: String) = saver.run(force = true) { repository.deleteRecipe(id) }

    // GLOBAL solo para admins; el resto siempre PRIVATE aunque llegue otra cosa (las reglas también lo exigen).
    private fun safeVisibility(visibility: RecipeVisibility) =
        if (visibility == RecipeVisibility.GLOBAL && !mutableUiState.value.isAdmin) {
            RecipeVisibility.PRIVATE
        } else {
            visibility
        }

    private fun validate(
        title: String,
        description: String?,
        category: String?,
        steps: List<String>,
        servings: Int?,
        totalMinutes: Int?,
    ): Boolean {
        val error = RecipesValidation.validate(title, description, category, steps, servings, totalMinutes)
            ?: return true
        saver.showEditorError(error)
        return false
    }
}

private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun ImportFailure.toUiMessage(): RecipeUiMessage = when (this) {
    ImportFailure.InvalidUrl -> RecipeUiMessage.ImportInvalidUrl
    ImportFailure.Network -> RecipeUiMessage.ImportNetwork
    ImportFailure.NotHtml -> RecipeUiMessage.ImportNotHtml
    ImportFailure.NoRecipeFound -> RecipeUiMessage.ImportNoRecipe
    ImportFailure.TooLarge -> RecipeUiMessage.ImportTooLarge
}

private fun Throwable.toUiMessage(): RecipeUiMessage = when (this) {
    is RecipeRepositoryException -> failure.toUiMessage()
    is ShoppingRepositoryException -> failure.toUiMessage()
    is SaveTimeoutException -> RecipeUiMessage.SaveTimeout
    else -> RecipeUiMessage.UnexpectedError
}

private fun RecipeFailure.toUiMessage(): RecipeUiMessage = when (this) {
    RecipeFailure.TitleRequired -> RecipeUiMessage.TitleRequired
    RecipeFailure.TitleTooLong -> RecipeUiMessage.TitleTooLong
    RecipeFailure.DescriptionTooLong -> RecipeUiMessage.DescriptionTooLong
    RecipeFailure.CategoryTooLong -> RecipeUiMessage.CategoryTooLong
    RecipeFailure.TooManySteps,
    RecipeFailure.StepTooLong,
    RecipeFailure.ServingsOutOfRange,
    RecipeFailure.TotalMinutesOutOfRange,
    -> detailsMessage()
    RecipeFailure.NotAuthenticated -> RecipeUiMessage.NotAuthenticated
    RecipeFailure.EmailNotVerified -> RecipeUiMessage.EmailNotVerified
    RecipeFailure.RecipeNotFound -> RecipeUiMessage.RecipeNotFound
    RecipeFailure.PermissionDenied -> RecipeUiMessage.PermissionDenied
    RecipeFailure.Network -> RecipeUiMessage.NetworkError
    RecipeFailure.Unknown -> RecipeUiMessage.UnexpectedError
}

// Pasos, raciones y tiempo fuera de los límites (aparte para no inflar la complejidad de toUiMessage).
private fun RecipeFailure.detailsMessage(): RecipeUiMessage = when (this) {
    RecipeFailure.TooManySteps -> RecipeUiMessage.TooManySteps
    RecipeFailure.StepTooLong -> RecipeUiMessage.StepTooLong
    RecipeFailure.ServingsOutOfRange -> RecipeUiMessage.ServingsInvalid
    RecipeFailure.TotalMinutesOutOfRange -> RecipeUiMessage.TotalMinutesInvalid
    else -> RecipeUiMessage.UnexpectedError
}

private fun ShoppingFailure.toUiMessage(): RecipeUiMessage = when (this) {
    ShoppingFailure.NotAuthenticated -> RecipeUiMessage.NotAuthenticated
    ShoppingFailure.EmailNotVerified -> RecipeUiMessage.EmailNotVerified
    ShoppingFailure.PermissionDenied -> RecipeUiMessage.PermissionDenied
    ShoppingFailure.Network -> RecipeUiMessage.NetworkError
    ShoppingFailure.ItemChanged -> RecipeUiMessage.ShoppingChanged
    else -> RecipeUiMessage.UnexpectedError
}
