package com.dlunaunizar.bobitos.feature.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.failed
import com.dlunaunizar.bobitos.core.common.started
import com.dlunaunizar.bobitos.core.common.succeeded
import com.dlunaunizar.bobitos.core.common.withSaveTimeout
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.data.recipeimport.ImportFailure
import com.dlunaunizar.bobitos.data.recipeimport.RecipeImportException
import com.dlunaunizar.bobitos.data.recipeimport.RecipeImporter
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.RecipeFailure
import com.dlunaunizar.bobitos.data.repository.RecipeRepository
import com.dlunaunizar.bobitos.data.repository.RecipeRepositoryException
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.feature.common.applyIngredientReview
import com.dlunaunizar.bobitos.feature.common.buildIngredientReviewRows
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val repository: RecipeRepository,
    private val importer: RecipeImporter,
    private val shoppingRepository: ShoppingRepository,
    private val ingredientPrefsRepository: IngredientPrefsRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(RecipesUiState())
    val uiState: StateFlow<RecipesUiState> = mutableUiState.asStateFlow()

    private var globalJob: Job? = null
    private var mineJob: Job? = null
    private var prefsJob: Job? = null
    private var observing = false

    // Preferencias del usuario (super/marca por defecto), para prerrellenar al añadir a la compra.
    private var ingredientPrefs: Map<String, IngredientPref> = emptyMap()

    fun observe() {
        if (observing) return
        observing = true
        mutableUiState.update { it.copy(isAdmin = repository.isCurrentUserRecipeAdmin()) }
        prefsJob = viewModelScope.launch {
            ingredientPrefsRepository.prefs()
                .catch { ingredientPrefs = emptyMap() }
                .collect { prefs -> ingredientPrefs = prefs }
        }
        globalJob = viewModelScope.launch {
            repository.globalRecipes()
                .catch { error -> mutableUiState.update { it.copy(global = UiState.Error(error.message)) } }
                .collect { recipes -> mutableUiState.update { it.copy(global = UiState.Content(recipes)) } }
        }
        mineJob = viewModelScope.launch {
            repository.myRecipes()
                .catch { error -> mutableUiState.update { it.copy(mine = UiState.Error(error.message)) } }
                .collect { recipes -> mutableUiState.update { it.copy(mine = UiState.Content(recipes)) } }
        }
    }

    fun stopObserving() {
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
            showError(RecipeUiMessage.NoIngredients)
            return
        }
        viewModelScope.launch {
            val current = runCatching { shoppingRepository.items(spaceId).first() }.getOrDefault(emptyList())
            mutableUiState.update { it.copy(ingredientReview = buildIngredientReviewRows(ingredients, current)) }
        }
    }

    fun confirmShoppingReview(spaceId: String, finalQuantities: List<String?>) {
        val rows = mutableUiState.value.ingredientReview ?: return
        mutableUiState.update { it.copy(ingredientReview = null) }
        runAction(RecipeUiMessage.AddedToShopping) {
            applyIngredientReview(spaceId, rows, finalQuantities, ingredientPrefs, shoppingRepository)
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
    ) {
        if (!validate(title, description, category)) return
        runAction(RecipeUiMessage.RecipeSaved, editor = true) {
            repository.createRecipe(
                visibility = safeVisibility(visibility),
                title = title.trim(),
                description = description.normalized(),
                category = category.normalized(),
                sourceRecipeId = null,
                ingredients = ingredients,
                sourceUrl = sourceUrl,
            )
        }
    }

    /** Descarga la receta de [url] (schema.org/Recipe) y, si tiene éxito, la deja en `importDraft`. */
    fun importFromUrl(url: String) {
        if (mutableUiState.value.isImporting) return
        mutableUiState.update { it.copy(isImporting = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                val draft = importer.import(url)
                mutableUiState.update { it.copy(isImporting = false, importDraft = draft) }
            } catch (error: RecipeImportException) {
                mutableUiState.update { it.copy(isImporting = false, error = error.failure.toUiMessage()) }
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
    ) {
        if (!validate(title, description, category)) return
        runAction(RecipeUiMessage.RecipeSaved, editor = true) {
            repository.updateRecipe(
                recipeId,
                title.trim(),
                description.normalized(),
                category.normalized(),
                ingredients,
            )
        }
    }

    // Recrea una receta borrada («Deshacer»): no es un guardado del editor, no mueve su estado.
    fun restoreRecipe(recipe: Recipe) = runAction(RecipeUiMessage.RecipeSaved) {
        repository.createRecipe(
            visibility = safeVisibility(recipe.visibility),
            title = recipe.title,
            description = recipe.description,
            category = recipe.category,
            sourceRecipeId = null,
            ingredients = recipe.ingredients.orEmpty(),
            sourceUrl = null,
        )
    }

    fun deleteRecipe(recipeId: String) {
        // Sin notice: el feedback del borrado (con «Deshacer») lo da el Snackbar de la pantalla.
        runAction(null) { repository.deleteRecipe(recipeId) }
    }

    fun fork(source: Recipe) {
        runAction(RecipeUiMessage.RecipeForked) {
            repository.createRecipe(
                visibility = RecipeVisibility.PRIVATE,
                title = source.title,
                description = source.description,
                category = source.category,
                sourceRecipeId = source.id,
                ingredients = source.ingredients.orEmpty(),
            )
        }
    }

    fun consumeEditorSave() {
        mutableUiState.update { it.copy(editorSave = EditorSaveStatus.IDLE) }
    }

    fun clearFeedback() {
        mutableUiState.update { it.copy(error = null, notice = null) }
    }

    // GLOBAL solo para admins; el resto siempre PRIVATE aunque llegue otra cosa (las reglas también lo exigen).
    private fun safeVisibility(visibility: RecipeVisibility) =
        if (visibility == RecipeVisibility.GLOBAL && !mutableUiState.value.isAdmin) {
            RecipeVisibility.PRIVATE
        } else {
            visibility
        }

    private fun validate(title: String, description: String?, category: String?): Boolean {
        val error = RecipesValidation.validate(title, description, category) ?: return true
        showError(error, editor = true)
        return false
    }

    private fun showError(message: RecipeUiMessage, editor: Boolean = false) {
        mutableUiState.update {
            it.copy(isSaving = false, error = message, notice = null, editorSave = it.editorSave.failed(editor))
        }
    }

    private fun runAction(successNotice: RecipeUiMessage?, editor: Boolean = false, action: suspend () -> Unit) {
        if (mutableUiState.value.isSaving) return
        mutableUiState.update {
            it.copy(isSaving = true, error = null, notice = null, editorSave = it.editorSave.started(editor))
        }
        viewModelScope.launch {
            runCatching { withSaveTimeout { action() } }
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
    RecipeFailure.NotAuthenticated -> RecipeUiMessage.NotAuthenticated
    RecipeFailure.EmailNotVerified -> RecipeUiMessage.EmailNotVerified
    RecipeFailure.RecipeNotFound -> RecipeUiMessage.RecipeNotFound
    RecipeFailure.PermissionDenied -> RecipeUiMessage.PermissionDenied
    RecipeFailure.Network -> RecipeUiMessage.NetworkError
    RecipeFailure.Unknown -> RecipeUiMessage.UnexpectedError
}

private fun ShoppingFailure.toUiMessage(): RecipeUiMessage = when (this) {
    ShoppingFailure.NotAuthenticated -> RecipeUiMessage.NotAuthenticated
    ShoppingFailure.EmailNotVerified -> RecipeUiMessage.EmailNotVerified
    ShoppingFailure.PermissionDenied -> RecipeUiMessage.PermissionDenied
    ShoppingFailure.Network -> RecipeUiMessage.NetworkError
    else -> RecipeUiMessage.UnexpectedError
}
