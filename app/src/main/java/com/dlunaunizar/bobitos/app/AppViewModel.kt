package com.dlunaunizar.bobitos.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppViewModel @Inject constructor(
    spaceRepository: SpaceRepository,
) : ViewModel() {
    private val selectedSpaceId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AppUiState> = combine(
        spaceRepository.spaces,
        selectedSpaceId,
    ) { spaces, selectedId ->
        AppUiState(
            spaces = UiState.Content(spaces),
            selectedSpace = spaces.firstOrNull { it.id == selectedId },
        )
    }.catch { error ->
        emit(
            AppUiState(
                spaces = UiState.Error(error.message),
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState(),
    )

    fun selectSpace(spaceId: String) {
        selectedSpaceId.value = spaceId
    }
}

