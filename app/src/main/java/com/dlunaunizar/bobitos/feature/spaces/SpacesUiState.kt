package com.dlunaunizar.bobitos.feature.spaces

import com.dlunaunizar.bobitos.core.common.SaveState
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SpaceMember

data class SpaceManagementUiState(
    val members: UiState<List<SpaceMember>> = UiState.Loading,
    val invitations: UiState<List<SpaceInvitation>> = UiState.Loading,
    val acceptedSpaceId: String? = null,
    val save: SaveState<SpaceUiMessage, SpaceUiMessage> = SaveState(),
)

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
    SaveTimeout,
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
