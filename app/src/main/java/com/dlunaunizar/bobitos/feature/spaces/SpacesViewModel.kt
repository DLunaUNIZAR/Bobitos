package com.dlunaunizar.bobitos.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.InvitationCode
import com.dlunaunizar.bobitos.data.repository.SpaceFailure
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepositoryException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SpacesViewModel @Inject constructor(
    private val spaceRepository: SpaceRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SpaceManagementUiState())
    val uiState: StateFlow<SpaceManagementUiState> = mutableUiState.asStateFlow()
    private var membersJob: Job? = null
    private var invitationsJob: Job? = null

    fun observeSpaceSettings(spaceId: String, includeInvitations: Boolean) {
        membersJob?.cancel()
        mutableUiState.update { it.copy(members = UiState.Loading) }
        membersJob = viewModelScope.launch {
            spaceRepository.members(spaceId)
                .catch { error ->
                    mutableUiState.update { state ->
                        state.copy(members = UiState.Error(error.message))
                    }
                }
                .collect { members ->
                    mutableUiState.update { state ->
                        state.copy(members = UiState.Content(members))
                    }
                }
        }
        invitationsJob?.cancel()
        if (includeInvitations) {
            mutableUiState.update { it.copy(invitations = UiState.Loading) }
            invitationsJob = viewModelScope.launch {
                spaceRepository.invitations(spaceId)
                    .catch { error ->
                        mutableUiState.update { state ->
                            state.copy(invitations = UiState.Error(error.message))
                        }
                    }
                    .collect { invitations ->
                        mutableUiState.update { state ->
                            state.copy(invitations = UiState.Content(invitations))
                        }
                    }
            }
        } else {
            mutableUiState.update { it.copy(invitations = UiState.Content(emptyList())) }
        }
    }

    fun stopObservingSpaceSettings() {
        membersJob?.cancel()
        membersJob = null
        invitationsJob?.cancel()
        invitationsJob = null
    }

    fun createSpace(name: String) {
        val error = SpaceValidation.validateName(name)
        if (error != null) {
            showValidationError(error)
            return
        }
        runAction(SpaceUiMessage.SpaceCreated) {
            spaceRepository.createSpace(name.trim())
        }
    }

    fun renameSpace(spaceId: String, name: String) {
        val error = SpaceValidation.validateName(name)
        if (error != null) {
            showValidationError(error)
            return
        }
        runAction(SpaceUiMessage.SpaceRenamed) {
            spaceRepository.renameSpace(spaceId, name.trim())
        }
    }

    fun leaveSpace(spaceId: String) {
        runAction(SpaceUiMessage.SpaceLeft) {
            spaceRepository.leaveSpace(spaceId)
        }
    }

    fun removeMember(spaceId: String, userId: String) {
        runAction(SpaceUiMessage.MemberRemoved) {
            spaceRepository.removeMember(spaceId, userId)
        }
    }

    fun transferOwnership(spaceId: String, newOwnerId: String) {
        runAction(SpaceUiMessage.OwnershipTransferred) {
            spaceRepository.transferOwnership(spaceId, newOwnerId)
        }
    }

    fun createInvitation(spaceId: String) {
        runAction(SpaceUiMessage.InvitationCreated) {
            spaceRepository.createInvitation(spaceId)
        }
    }

    fun revokeInvitation(invitationId: String) {
        runAction(SpaceUiMessage.InvitationRevokedNotice) {
            spaceRepository.revokeInvitation(invitationId)
        }
    }

    fun acceptInvitation(code: String) {
        if (InvitationCode.normalize(code) == null) {
            showValidationError(SpaceUiMessage.InvalidInvitationCode)
            return
        }
        runAction(SpaceUiMessage.InvitationAccepted) {
            val spaceId = spaceRepository.acceptInvitation(code)
            mutableUiState.update { state -> state.copy(acceptedSpaceId = spaceId) }
        }
    }

    fun consumeAcceptedSpace() {
        mutableUiState.update { it.copy(acceptedSpaceId = null) }
    }

    fun clearFeedback() {
        mutableUiState.update { it.copy(error = null, notice = null) }
    }

    private fun showValidationError(message: SpaceUiMessage) {
        mutableUiState.update {
            it.copy(isLoading = false, error = message, notice = null)
        }
    }

    private fun runAction(
        successNotice: SpaceUiMessage,
        action: suspend () -> Unit,
    ) {
        if (mutableUiState.value.isLoading) return
        mutableUiState.update {
            it.copy(isLoading = true, error = null, notice = null)
        }
        viewModelScope.launch {
            try {
                action()
                mutableUiState.update {
                    it.copy(isLoading = false, notice = successNotice)
                }
            } catch (error: Throwable) {
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.toUiMessage(),
                        notice = null,
                    )
                }
            }
        }
    }
}

private fun Throwable.toUiMessage(): SpaceUiMessage {
    return when ((this as? SpaceRepositoryException)?.failure) {
        SpaceFailure.NameRequired -> SpaceUiMessage.NameRequired
        SpaceFailure.NameTooLong -> SpaceUiMessage.NameTooLong
        SpaceFailure.NotAuthenticated -> SpaceUiMessage.NotAuthenticated
        SpaceFailure.EmailNotVerified -> SpaceUiMessage.EmailNotVerified
        SpaceFailure.SpaceNotFound -> SpaceUiMessage.SpaceNotFound
        SpaceFailure.MembershipNotFound -> SpaceUiMessage.MembershipNotFound
        SpaceFailure.OwnerMustTransfer -> SpaceUiMessage.OwnerMustTransfer
        SpaceFailure.CannotRemoveOwner -> SpaceUiMessage.CannotRemoveOwner
        SpaceFailure.InvalidNewOwner -> SpaceUiMessage.InvalidNewOwner
        SpaceFailure.InvalidInvitationCode -> SpaceUiMessage.InvalidInvitationCode
        SpaceFailure.InvitationNotFound -> SpaceUiMessage.InvitationNotFound
        SpaceFailure.InvitationAlreadyUsed -> SpaceUiMessage.InvitationAlreadyUsed
        SpaceFailure.InvitationRevoked -> SpaceUiMessage.InvitationRevoked
        SpaceFailure.InvitationExpired -> SpaceUiMessage.InvitationExpired
        SpaceFailure.SpaceFull -> SpaceUiMessage.SpaceFull
        SpaceFailure.PermissionDenied -> SpaceUiMessage.PermissionDenied
        SpaceFailure.Network -> SpaceUiMessage.NetworkError
        SpaceFailure.Unknown,
        null -> SpaceUiMessage.UnexpectedError
    }
}
