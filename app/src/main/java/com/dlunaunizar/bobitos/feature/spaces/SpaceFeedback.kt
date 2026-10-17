package com.dlunaunizar.bobitos.feature.spaces

import com.dlunaunizar.bobitos.R

internal val SpaceUiMessage.stringResourceId: Int
    get() = when (this) {
        SpaceUiMessage.NameRequired -> R.string.space_error_name_required
        SpaceUiMessage.NameTooLong -> R.string.space_error_name_too_long
        SpaceUiMessage.NotAuthenticated -> R.string.space_error_not_authenticated
        SpaceUiMessage.EmailNotVerified -> R.string.space_error_email_not_verified
        SpaceUiMessage.SpaceNotFound -> R.string.space_error_not_found
        SpaceUiMessage.MembershipNotFound -> R.string.space_error_membership_not_found
        SpaceUiMessage.OwnerMustTransfer -> R.string.space_error_owner_must_transfer
        SpaceUiMessage.CannotRemoveOwner -> R.string.space_error_cannot_remove_owner
        SpaceUiMessage.InvalidNewOwner -> R.string.space_error_invalid_new_owner
        SpaceUiMessage.OnlyOwnerCanDelete -> R.string.space_error_only_owner_delete
        SpaceUiMessage.InvalidInvitationCode -> R.string.invitation_error_invalid_code
        SpaceUiMessage.InvitationNotFound -> R.string.invitation_error_not_found
        SpaceUiMessage.InvitationAlreadyUsed -> R.string.invitation_error_already_used
        SpaceUiMessage.InvitationRevoked -> R.string.invitation_error_revoked
        SpaceUiMessage.InvitationExpired -> R.string.invitation_error_expired
        SpaceUiMessage.SpaceFull -> R.string.invitation_error_space_full
        SpaceUiMessage.PermissionDenied -> R.string.space_error_permission_denied
        SpaceUiMessage.NetworkError -> R.string.space_error_network
        SpaceUiMessage.UnexpectedError -> R.string.space_error_unexpected
        SpaceUiMessage.SaveTimeout -> R.string.write_timeout
        SpaceUiMessage.SpaceCreated -> R.string.space_notice_created
        SpaceUiMessage.SpaceRenamed -> R.string.space_notice_renamed
        SpaceUiMessage.SpaceDeleted -> R.string.space_notice_deleted
        SpaceUiMessage.SpaceLeft -> R.string.space_notice_left
        SpaceUiMessage.MemberRemoved -> R.string.space_notice_member_removed
        SpaceUiMessage.OwnershipTransferred -> R.string.space_notice_ownership_transferred
        SpaceUiMessage.InvitationCreated -> R.string.invitation_notice_created
        SpaceUiMessage.InvitationRevokedNotice -> R.string.invitation_notice_revoked
        SpaceUiMessage.InvitationAccepted -> R.string.invitation_notice_accepted
    }
