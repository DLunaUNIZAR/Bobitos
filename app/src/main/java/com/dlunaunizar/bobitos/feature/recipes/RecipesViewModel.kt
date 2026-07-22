package com.dlunaunizar.bobitos.feature.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.data.recipeimport.ImportFailure
import com.dlunaunizar.bobitos.data.recipeimport.RecipeImportException
import com.dlunaunizar.bobitos.data.recipeimport.RecipeImporter
import com.dlunaunizar.bobitos.data.repository.RecipeFailure
import com.dlunaunizar.bobitos.data.repository.RecipeRepository
import com.dlunaunizar.bobitos.data.repository.RecipeRepositoryException
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
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(RecipesUiState())
    val uiState: StateFlow<RecipesUiState> = mutableUiState.asStateFlow()

    private var globalJob: Job? = null
    private var mineJob: Job? = null
    private var observing = false

    fun observe() {
        if (observing) return
        observing = true
        mutableUiState.update { it.copy(isAdmin = repository.isCurrentUserRecipeAdmin()) }
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
        globalJob = null
        mineJob = null
        observing = false
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
        // GLOBAL solo para admins; el resto siempre PRIVATE aunque llegue otra cosa (las reglas también lo exigen).
        val safeVisibility = if (visibility == RecipeVisibility.GLOBAL && !mutableUiState.value.isAdmin) {
            RecipeVisibility.PRIVATE
        } else {
            visibility
        }
        runAction(RecipeUiMessage.RecipeSaved) {
            repository.createRecipe(
                visibility = safeVisibility,
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
        runAction(RecipeUiMessage.RecipeSaved) {
            repository.updateRecipe(
                recipeId,
                title.trim(),
                description.normalized(),
                category.normalized(),
                ingredients,
            )
        }
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

    fun clearFeedback() {
        mutableUiState.update { it.copy(error = null, notice = null) }
    }

    private fun validate(title: String, description: String?, category: String?): Boolean {
        val error = RecipesValidation.validate(title, description, category) ?: return true
        showError(error)
        return false
    }

    private fun showError(message: RecipeUiMessage) {
        mutableUiState.update { it.copy(isSaving = false, error = message, notice = null) }
    }

    private fun runAction(successNotice: RecipeUiMessage?, action: suspend () -> Unit) {
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

private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun ImportFailure.toUiMessage(): RecipeUiMessage = when (this) {
    ImportFailure.InvalidUrl -> RecipeUiMessage.ImportInvalidUrl
    ImportFailure.Network -> RecipeUiMessage.ImportNetwork
    ImportFailure.NotHtml -> RecipeUiMessage.ImportNotHtml
    ImportFailure.NoRecipeFound -> RecipeUiMessage.ImportNoRecipe
    ImportFailure.TooLarge -> RecipeUiMessage.ImportTooLarge
}

private fun Throwable.toUiMessage(): RecipeUiMessage = when ((this as? RecipeRepositoryException)?.failure) {
    RecipeFailure.TitleRequired -> RecipeUiMessage.TitleRequired
    RecipeFailure.TitleTooLong -> RecipeUiMessage.TitleTooLong
    RecipeFailure.DescriptionTooLong -> RecipeUiMessage.DescriptionTooLong
    RecipeFailure.CategoryTooLong -> RecipeUiMessage.CategoryTooLong
    RecipeFailure.NotAuthenticated -> RecipeUiMessage.NotAuthenticated
    RecipeFailure.EmailNotVerified -> RecipeUiMessage.EmailNotVerified
    RecipeFailure.RecipeNotFound -> RecipeUiMessage.RecipeNotFound
    RecipeFailure.PermissionDenied -> RecipeUiMessage.PermissionDenied
    RecipeFailure.Network -> RecipeUiMessage.NetworkError
    RecipeFailure.Unknown,
    null,
    -> RecipeUiMessage.UnexpectedError
}
