package com.dlunaunizar.bobitos.feature.spaces

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.SpaceMember

data class SpaceManagementUiState(
    val isLoading: Boolean = false,
    val members: UiState<List<SpaceMember>> = UiState.Loading,
    val error: SpaceUiMessage? = null,
    val notice: SpaceUiMessage? = null,
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
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    SpaceCreated,
    SpaceRenamed,
    SpaceLeft,
    MemberRemoved,
    OwnershipTransferred,
}
