package com.dlunaunizar.bobitos.feature.spaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R

@Composable
internal fun SpaceFeedback(
    state: SpaceManagementUiState,
    onDismiss: () -> Unit,
) {
    val message = state.error ?: state.notice ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(message.stringResourceId),
            color = if (state.error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) {
            Text(text = stringResource(R.string.dismiss))
        }
    }
}

private val SpaceUiMessage.stringResourceId: Int
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
        SpaceUiMessage.InvalidInvitationCode -> R.string.invitation_error_invalid_code
        SpaceUiMessage.InvitationNotFound -> R.string.invitation_error_not_found
        SpaceUiMessage.InvitationAlreadyUsed -> R.string.invitation_error_already_used
        SpaceUiMessage.InvitationRevoked -> R.string.invitation_error_revoked
        SpaceUiMessage.InvitationExpired -> R.string.invitation_error_expired
        SpaceUiMessage.SpaceFull -> R.string.invitation_error_space_full
        SpaceUiMessage.PermissionDenied -> R.string.space_error_permission_denied
        SpaceUiMessage.NetworkError -> R.string.space_error_network
        SpaceUiMessage.UnexpectedError -> R.string.space_error_unexpected
        SpaceUiMessage.SpaceCreated -> R.string.space_notice_created
        SpaceUiMessage.SpaceRenamed -> R.string.space_notice_renamed
        SpaceUiMessage.SpaceLeft -> R.string.space_notice_left
        SpaceUiMessage.MemberRemoved -> R.string.space_notice_member_removed
        SpaceUiMessage.OwnershipTransferred -> R.string.space_notice_ownership_transferred
        SpaceUiMessage.InvitationCreated -> R.string.invitation_notice_created
        SpaceUiMessage.InvitationRevokedNotice -> R.string.invitation_notice_revoked
        SpaceUiMessage.InvitationAccepted -> R.string.invitation_notice_accepted
    }
