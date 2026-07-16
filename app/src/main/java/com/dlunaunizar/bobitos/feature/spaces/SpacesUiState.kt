package com.dlunaunizar.bobitos.feature.spaces

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceInvitation

data class SpaceManagementUiState(
    val isLoading: Boolean = false,
    val writeStatus: WriteStatus = WriteStatus.IDLE,
    val members: UiState<List<SpaceMember>> = UiState.Loading,
    val invitations: UiState<List<SpaceInvitation>> = UiState.Loading,
    val acceptedSpaceId: String? = null,
    val error: SpaceUiMessage? = null,
    val notice: SpaceUiMessage? = null,
)

enum class WriteStatus {
    IDLE,
    SAVING,
    SAVED,
    ERROR,
}

enum class SpaceUiMessage {
    NameRequired,
    NameTooLong,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    MembershipNotFound,
    OwnerMustTransfer,
    CannotRemoveOwner,
    InvalidNewOwner,
    OnlyOwnerCanDelete,
    InvalidInvitationCode,
    InvitationNotFound,
    InvitationAlreadyUsed,
    InvitationRevoked,
    InvitationExpired,
    SpaceFull,
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    SpaceCreated,
    SpaceRenamed,
    SpaceDeleted,
    SpaceLeft,
    MemberRemoved,
    OwnershipTransferred,
    InvitationCreated,
    InvitationRevokedNotice,
    InvitationAccepted,
}
