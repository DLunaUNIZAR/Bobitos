package com.dlunaunizar.bobitos.feature.ingredients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.slug
import com.dlunaunizar.bobitos.data.repository.IngredientFailure
import com.dlunaunizar.bobitos.data.repository.IngredientPrefFailure
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsException
import com.dlunaunizar.bobitos.data.repository.IngredientPrefsRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepository
import com.dlunaunizar.bobitos.data.repository.IngredientRepositoryException
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
class IngredientsViewModel @Inject constructor(
    private val repository: IngredientRepository,
    private val prefsRepository: IngredientPrefsRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(IngredientsUiState())
    val uiState: StateFlow<IngredientsUiState> = mutableUiState.asStateFlow()

    private var catalogJob: Job? = null
    private var prefsJob: Job? = null
    private var observing = false

    fun observe() {
        if (observing) return
        observing = true
        mutableUiState.update {
            it.copy(
                isAdmin = repository.isCurrentUserCatalogAdmin(),
                currentUid = repository.currentUserId(),
            )
        }
        catalogJob = viewModelScope.launch {
            repository.catalog()
                .catch { error -> mutableUiState.update { it.copy(catalog = UiState.Error(error.message)) } }
                .collect { list -> mutableUiState.update { it.copy(catalog = UiState.Content(list)) } }
        }
        prefsJob = viewModelScope.launch {
            prefsRepository.prefs()
                .catch { mutableUiState.update { it.copy(prefs = emptyMap()) } }
                .collect { prefs -> mutableUiState.update { it.copy(prefs = prefs) } }
        }
    }

    fun stopObserving() {
        catalogJob?.cancel()
        prefsJob?.cancel()
        catalogJob = null
        prefsJob = null
        observing = false
    }

    fun setQuery(query: String) {
        mutableUiState.update { it.copy(query = query) }
    }

    fun createIngredient(name: String, category: String?, defaultUnit: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            showError(IngredientUiMessage.NameRequired)
            return
        }
        if (catalogContains(slug(trimmed))) {
            showError(IngredientUiMessage.AlreadyExists)
            return
        }
        runAction(IngredientUiMessage.Saved) { repository.createIngredient(trimmed, category, defaultUnit) }
    }

    fun updateIngredient(id: String, name: String, category: String?, defaultUnit: String?) {
        if (name.trim().isEmpty()) {
            showError(IngredientUiMessage.NameRequired)
            return
        }
        runAction(IngredientUiMessage.Saved) { repository.updateIngredient(id, name.trim(), category, defaultUnit) }
    }

    fun deleteIngredient(id: String) {
        runAction(IngredientUiMessage.Deleted) { repository.deleteIngredient(id) }
    }

    fun setPref(ingredientId: String, supermarket: Supermarket?, brand: String?) {
        runAction(IngredientUiMessage.PrefSaved) { prefsRepository.setPref(ingredientId, supermarket, brand) }
    }

    fun clearPref(ingredientId: String) {
        runAction(IngredientUiMessage.PrefCleared) { prefsRepository.clearPref(ingredientId) }
    }

    fun clearFeedback() {
        mutableUiState.update { it.copy(error = null, notice = null) }
    }

    private fun catalogContains(id: String): Boolean =
        (mutableUiState.value.catalog as? UiState.Content)?.value?.any { it.id == id } == true

    private fun showError(message: IngredientUiMessage) {
        mutableUiState.update { it.copy(isSaving = false, error = message, notice = null) }
    }

    private fun runAction(successNotice: IngredientUiMessage, action: suspend () -> Unit) {
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

private fun Throwable.toUiMessage(): IngredientUiMessage = when (this) {
    is IngredientRepositoryException -> failure.toUiMessage()
    is IngredientPrefsException -> failure.toUiMessage()
    else -> IngredientUiMessage.UnexpectedError
}

private fun IngredientFailure.toUiMessage(): IngredientUiMessage = when (this) {
    IngredientFailure.NameRequired -> IngredientUiMessage.NameRequired
    IngredientFailure.NameTooLong -> IngredientUiMessage.NameTooLong
    IngredientFailure.CategoryTooLong -> IngredientUiMessage.CategoryTooLong
    IngredientFailure.UnitTooLong -> IngredientUiMessage.UnitTooLong
    IngredientFailure.NotAuthenticated -> IngredientUiMessage.NotAuthenticated
    IngredientFailure.EmailNotVerified -> IngredientUiMessage.EmailNotVerified
    IngredientFailure.IngredientNotFound -> IngredientUiMessage.NotFound
    IngredientFailure.PermissionDenied -> IngredientUiMessage.PermissionDenied
    IngredientFailure.Network -> IngredientUiMessage.NetworkError
    IngredientFailure.Unknown -> IngredientUiMessage.UnexpectedError
}

private fun IngredientPrefFailure.toUiMessage(): IngredientUiMessage = when (this) {
    IngredientPrefFailure.NotAuthenticated -> IngredientUiMessage.NotAuthenticated
    IngredientPrefFailure.EmailNotVerified -> IngredientUiMessage.EmailNotVerified
    IngredientPrefFailure.PermissionDenied -> IngredientUiMessage.PermissionDenied
    IngredientPrefFailure.Network -> IngredientUiMessage.NetworkError
    IngredientPrefFailure.Unknown -> IngredientUiMessage.UnexpectedError
}
