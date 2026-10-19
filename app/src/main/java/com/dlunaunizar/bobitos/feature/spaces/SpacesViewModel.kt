package com.dlunaunizar.bobitos.feature.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.core.common.SaveFeedbackControls
import com.dlunaunizar.bobitos.core.common.SaveRunner
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.toFailureKind
import com.dlunaunizar.bobitos.core.model.InvitationCode
import com.dlunaunizar.bobitos.data.repository.SpaceFailure
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepositoryException
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
class SpacesViewModel @Inject constructor(private val spaceRepository: SpaceRepository) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SpaceManagementUiState())
    val uiState: StateFlow<SpaceManagementUiState> = mutableUiState.asStateFlow()
    private val saver = SaveRunner(
        viewModelScope,
        mutableUiState,
        SpaceManagementUiState::save,
        { copy(save = it) },
        Throwable::toUiMessage,
    )
    val feedback: SaveFeedbackControls get() = saver
    private var membersJob: Job? = null
    private var invitationsJob: Job? = null

    fun observeSpaceSettings(spaceId: String, includeInvitations: Boolean) {
        membersJob?.cancel()
        mutableUiState.update { it.copy(members = UiState.Loading) }
        membersJob = viewModelScope.launch {
            spaceRepository.members(spaceId)
                .catch { error ->
                    mutableUiState.update { state ->
                        state.copy(members = UiState.Error(error.toFailureKind()))
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
                            state.copy(invitations = UiState.Error(error.toFailureKind()))
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
            saver.showEditorError(error)
            return
        }
        saver.run(notice = SpaceUiMessage.SpaceCreated, editor = true) {
            spaceRepository.createSpace(name.trim())
        }
    }

    fun renameSpace(spaceId: String, name: String) {
        val error = SpaceValidation.validateName(name)
        if (error != null) {
            saver.showEditorError(error)
            return
        }
        saver.run(notice = SpaceUiMessage.SpaceRenamed, editor = true) {
            spaceRepository.renameSpace(spaceId, name.trim())
        }
    }

    fun leaveSpace(spaceId: String) {
        saver.run(notice = SpaceUiMessage.SpaceLeft) {
            spaceRepository.leaveSpace(spaceId)
        }
    }

    fun removeMember(spaceId: String, userId: String) {
        saver.run(notice = SpaceUiMessage.MemberRemoved) {
            spaceRepository.removeMember(spaceId, userId)
        }
    }

    fun transferOwnership(spaceId: String, newOwnerId: String) {
        saver.run(notice = SpaceUiMessage.OwnershipTransferred) {
            spaceRepository.transferOwnership(spaceId, newOwnerId)
        }
    }

    fun deleteSpace(spaceId: String) {
        saver.run(notice = SpaceUiMessage.SpaceDeleted) { spaceRepository.deleteSpace(spaceId) }
    }

    fun createInvitation(spaceId: String) {
        saver.run(notice = SpaceUiMessage.InvitationCreated) {
            spaceRepository.createInvitation(spaceId)
        }
    }

    fun revokeInvitation(invitationId: String) {
        saver.run(notice = SpaceUiMessage.InvitationRevokedNotice) {
            spaceRepository.revokeInvitation(invitationId)
        }
    }

    fun acceptInvitation(code: String) {
        if (InvitationCode.normalize(code) == null) {
            saver.showEditorError(SpaceUiMessage.InvalidInvitationCode)
            return
        }
        // El id y SAVED se publican a la vez, al confirmar.
        saver.run(
            notice = SpaceUiMessage.InvitationAccepted,
            editor = true,
            onSuccess = { spaceId -> copy(acceptedSpaceId = spaceId) },
        ) { spaceRepository.acceptInvitation(code) }
    }

    fun consumeAcceptedSpace() {
        mutableUiState.update { it.copy(acceptedSpaceId = null) }
    }

    // Lo usa también el marco de las demás pantallas del espacio: borra el error y el aviso de la pantalla.
    fun clearFeedback() = saver.clearFeedback()
}

private fun Throwable.toUiMessage(): SpaceUiMessage {
    if (this is SaveTimeoutException) return SpaceUiMessage.SaveTimeout
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
        SpaceFailure.OnlyOwnerCanDelete -> SpaceUiMessage.OnlyOwnerCanDelete
        SpaceFailure.InvalidInvitationCode -> SpaceUiMessage.InvalidInvitationCode
        SpaceFailure.InvitationNotFound -> SpaceUiMessage.InvitationNotFound
        SpaceFailure.InvitationAlreadyUsed -> SpaceUiMessage.InvitationAlreadyUsed
        SpaceFailure.InvitationRevoked -> SpaceUiMessage.InvitationRevoked
        SpaceFailure.InvitationExpired -> SpaceUiMessage.InvitationExpired
        SpaceFailure.SpaceFull -> SpaceUiMessage.SpaceFull
        SpaceFailure.PermissionDenied -> SpaceUiMessage.PermissionDenied
        SpaceFailure.Network -> SpaceUiMessage.NetworkError
        SpaceFailure.Unknown,
        null,
        -> SpaceUiMessage.UnexpectedError
    }
}
