package com.dlunaunizar.bobitos.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.data.connectivity.ConnectivityRepository
import com.dlunaunizar.bobitos.data.connectivity.NetworkStatus
import com.dlunaunizar.bobitos.data.repository.ActiveSpaceRepository
import com.dlunaunizar.bobitos.data.repository.AuthFailure
import com.dlunaunizar.bobitos.data.repository.AuthRepository
import com.dlunaunizar.bobitos.data.repository.AuthRepositoryException
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.sync.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RealtimeScope {
    AUTOMATIC,
    ALL_SPACES,
    ACTIVE_SPACE,
    PAUSED,
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val spaceRepository: SpaceRepository,
    private val activeSpaceRepository: ActiveSpaceRepository,
    private val connectivityRepository: ConnectivityRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {
    private val requestedSelection = MutableStateFlow<RequestedSelection?>(null)
    private val realtimeScope = MutableStateFlow(RealtimeScope.AUTOMATIC)
    private val sessionUser: StateFlow<UiState<AuthUser?>> = authRepository.currentUser
        .flatMapLatest { cachedUser ->
            if (cachedUser == null) {
                flowOf<UiState<AuthUser?>>(UiState.Content(null))
            } else {
                flow<UiState<AuthUser?>> {
                    try {
                        emit(UiState.Content(authRepository.refreshCurrentUser()))
                    } catch (error: AuthRepositoryException) {
                        if (error.failure == AuthFailure.Network) {
                            emit(UiState.Content(cachedUser))
                        } else {
                            emit(UiState.Error(error.message))
                        }
                    }
                }.onStart { emit(UiState.Loading) }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiState.Loading,
        )
    private val persistedSpaceId = sessionUser.flatMapLatest { authState ->
        val user = (authState as? UiState.Content)?.value
        if (user == null) {
            flowOf(null)
        } else {
            activeSpaceRepository.activeSpaceId(user.id)
        }
    }
    private val selectedSpaceId = combine(
        sessionUser,
        persistedSpaceId,
        requestedSelection,
    ) { authState, persistedId, requested ->
        val user = (authState as? UiState.Content)?.value
        requested
            ?.takeIf { selection -> selection.userId == user?.id }
            ?.spaceId
            ?: persistedId
    }
    private val observedSpaces: Flow<UiState<List<SpaceSummary>>> = combine(
        sessionUser,
        selectedSpaceId,
        realtimeScope,
    ) { authState, selectedId, scope ->
        val user = (authState as? UiState.Content)?.value
        ObservationRequest(user, selectedId, scope)
    }.distinctUntilChanged()
        .flatMapLatest { request -> request.observe() }

    val uiState: StateFlow<AppUiState> = combine(
        sessionUser,
        observedSpaces,
        selectedSpaceId,
        syncRepository.status,
    ) { authState, spaces, selectedId, syncStatus ->
        val availableSpaces = (spaces as? UiState.Content)?.value.orEmpty()
        AppUiState(
            authUser = authState,
            spaces = spaces,
            selectedSpace = availableSpaces.firstOrNull { it.id == selectedId },
            syncStatus = syncStatus,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState(),
    )

    init {
        viewModelScope.launch {
            combine(
                sessionUser,
                selectedSpaceId,
                connectivityRepository.status,
            ) { authState, selectedId, networkStatus ->
                val user = (authState as? UiState.Content)?.value
                SyncRequest(user, selectedId, networkStatus)
            }.distinctUntilChanged().collectLatest { request ->
                if (
                    request.networkStatus != NetworkStatus.ONLINE ||
                    request.user?.isEmailVerified != true
                ) {
                    syncRepository.markOffline()
                    return@collectLatest
                }

                syncRepository.requireRefresh()
                while (currentCoroutineContext().isActive) {
                    if (syncRepository.refresh(request.spaceId)) break
                    delay(SYNC_RETRY_MILLIS)
                }
            }
        }
    }

    fun selectSpace(spaceId: String) {
        val userId = (sessionUser.value as? UiState.Content)?.value?.id ?: return
        syncRepository.requireRefresh()
        requestedSelection.value = RequestedSelection(userId, spaceId)
        viewModelScope.launch {
            activeSpaceRepository.setActiveSpace(userId, spaceId)
        }
    }

    fun setRealtimeScope(scope: RealtimeScope) {
        realtimeScope.value = scope
    }

    private fun ObservationRequest.observe(): Flow<UiState<List<SpaceSummary>>> {
        if (user == null || !user.isEmailVerified) {
            return flowOf(UiState.Content(emptyList()))
        }
        val source = when (scope) {
            RealtimeScope.ALL_SPACES -> spaceRepository.spaces()
            RealtimeScope.ACTIVE_SPACE -> selectedId?.let { id ->
                spaceRepository.space(id).map { space -> listOfNotNull(space) }
            } ?: flowOf(emptyList())
            RealtimeScope.PAUSED -> emptyFlow()
            RealtimeScope.AUTOMATIC -> selectedId?.let { id ->
                spaceRepository.space(id).map { space -> listOfNotNull(space) }
            } ?: spaceRepository.spaces()
        }
        return source
            .map<List<SpaceSummary>, UiState<List<SpaceSummary>>> { spaces ->
                UiState.Content(spaces)
            }
            .catchAsUiState()
    }

    private data class RequestedSelection(
        val userId: String,
        val spaceId: String,
    )

    private data class ObservationRequest(
        val user: AuthUser?,
        val selectedId: String?,
        val scope: RealtimeScope,
    )

    private data class SyncRequest(
        val user: AuthUser?,
        val spaceId: String?,
        val networkStatus: NetworkStatus,
    )

    private companion object {
        const val SYNC_RETRY_MILLIS = 3_000L
    }
}

private fun Flow<UiState<List<SpaceSummary>>>.catchAsUiState(): Flow<UiState<List<SpaceSummary>>> =
    catch { error -> emit(UiState.Error(error.message)) }
