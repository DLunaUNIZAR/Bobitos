package com.dlunaunizar.bobitos.feature.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.data.repository.RecipeRepository
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
class RecipesViewModel @Inject constructor(private val repository: RecipeRepository) : ViewModel() {
    private val mutableUiState = MutableStateFlow(RecipesUiState())
    val uiState: StateFlow<RecipesUiState> = mutableUiState.asStateFlow()

    private var globalJob: Job? = null
    private var mineJob: Job? = null
    private var observing = false

    fun observe() {
        if (observing) return
        observing = true
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
}
