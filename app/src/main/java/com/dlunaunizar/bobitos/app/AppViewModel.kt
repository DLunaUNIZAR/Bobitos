package com.dlunaunizar.bobitos.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.data.repository.ActiveSpaceRepository
import com.dlunaunizar.bobitos.data.repository.AuthRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    spaceRepository: SpaceRepository,
    private val activeSpaceRepository: ActiveSpaceRepository,
) : ViewModel() {
    private val requestedSelection = MutableStateFlow<RequestedSelection?>(null)
    private val persistedSpaceId = authRepository.currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(null)
        } else {
            activeSpaceRepository.activeSpaceId(user.id)
        }
    }
    private val selectedSpaceId = combine(
        authRepository.currentUser,
        persistedSpaceId,
        requestedSelection,
    ) { user, persistedId, requested ->
        requested
            ?.takeIf { selection -> selection.userId == user?.id }
            ?.spaceId
            ?: persistedId
    }

    val uiState: StateFlow<AppUiState> = combine(
        authRepository.currentUser,
        spaceRepository.spaces,
        selectedSpaceId,
    ) { authUser, spaces, selectedId ->
        AppUiState(
            authUser = UiState.Content(authUser),
            spaces = UiState.Content(spaces),
            selectedSpace = spaces.firstOrNull { it.id == selectedId },
        )
    }.catch { error ->
        emit(
            AppUiState(
                authUser = UiState.Error(error.message),
                spaces = UiState.Error(error.message),
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState(),
    )

    fun selectSpace(spaceId: String) {
        val userId = authRepository.currentUser.value?.id ?: return
        requestedSelection.value = RequestedSelection(userId, spaceId)
        viewModelScope.launch {
            activeSpaceRepository.setActiveSpace(userId, spaceId)
        }
    }

    private data class RequestedSelection(
        val userId: String,
        val spaceId: String,
    )
}
