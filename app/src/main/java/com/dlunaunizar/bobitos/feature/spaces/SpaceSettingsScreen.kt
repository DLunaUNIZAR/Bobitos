package com.dlunaunizar.bobitos.feature.spaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.QrCodeImage
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.core.model.SyncStatus
import com.dlunaunizar.bobitos.feature.common.SyncStatusBanner
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SpaceSettingsScreen(
    space: SpaceSummary,
    currentUserId: String,
    state: SpaceManagementUiState,
    syncStatus: SyncStatus,
    canWrite: Boolean,
    onObserveSpaceSettings: (String, Boolean) -> Unit,
    onStopObservingSpaceSettings: () -> Unit,
    onRenameSpace: (String, String) -> Unit,
    onLeaveSpace: (String) -> Unit,
    onRemoveMember: (String, String) -> Unit,
    onTransferOwnership: (String, String) -> Unit,
    onDeleteSpace: (String) -> Unit,
    onCreateInvitation: (String) -> Unit,
    onRevokeInvitation: (String) -> Unit,
    onShareInvitation: (SpaceInvitation) -> Unit,
    onClearFeedback: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<MemberAction?>(null) }
    var showDeleteSpaceDialog by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(space.id, space.role) {
        onObserveSpaceSettings(space.id, space.role == SpaceRole.OWNER)
        onDispose(onStopObservingSpaceSettings)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(text = stringResource(R.string.navigate_back))
            }
            Text(
                text = stringResource(R.string.space_settings_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        Text(
            text = space.name,
            style = MaterialTheme.typography.headlineMedium,
        )
        SyncStatusBanner(status = syncStatus)
        Text(
            text = stringResource(
                if (space.role == SpaceRole.OWNER) {
                    R.string.space_role_owner
                } else {
                    R.string.space_role_member
                },
            ),
            style = MaterialTheme.typography.labelLarge,
        )
        SpaceFeedback(state = state, onDismiss = onClearFeedback)

        if (space.role == SpaceRole.OWNER) {
            Button(
                onClick = { showRenameDialog = true },
                enabled = !state.isLoading && canWrite,
            ) {
                Text(text = stringResource(R.string.space_rename))
            }

            HorizontalDivider()
            OwnerInvitations(
                invitations = state.invitations,
                spaceIsFull = space.memberCount >= 10,
                actionsEnabled = !state.isLoading && canWrite,
                onCreateInvitation = { onCreateInvitation(space.id) },
                onRevokeInvitation = onRevokeInvitation,
                onShareInvitation = onShareInvitation,
            )
        }

        HorizontalDivider()
        Text(
            text = stringResource(R.string.space_members_title),
            style = MaterialTheme.typography.titleLarge,
        )

        when (val members = state.members) {
            UiState.Loading -> CircularProgressIndicator()
            is UiState.Error -> Text(
                text = members.message ?: stringResource(R.string.generic_error),
                color = MaterialTheme.colorScheme.error,
            )
            is UiState.Content -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(members.value, key = SpaceMember::userId) { member ->
                    MemberCard(
                        member = member,
                        isCurrentUser = member.userId == currentUserId,
                        canManage = space.role == SpaceRole.OWNER,
                        actionsEnabled = !state.isLoading && canWrite,
                        onTransfer = {
                            pendingAction = MemberAction(
                                type = MemberActionType.Transfer,
                                memberId = member.userId,
                                memberName = member.displayName,
                            )
                        },
                        onRemove = {
                            pendingAction = MemberAction(
                                type = MemberActionType.Remove,
                                memberId = member.userId,
                                memberName = member.displayName,
                            )
                        },
                    )
                }
            }
        }

        if (space.role == SpaceRole.MEMBER) {
            TextButton(
                onClick = {
                    pendingAction = MemberAction(
                        type = MemberActionType.Leave,
                        memberId = currentUserId,
                        memberName = "",
                    )
                },
                enabled = !state.isLoading && canWrite,
            ) {
                Text(text = stringResource(R.string.space_leave))
            }
        } else {
            Text(
                text = stringResource(R.string.space_owner_leave_explanation),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = { showDeleteSpaceDialog = true },
                enabled = !state.isLoading && canWrite,
            ) { Text(stringResource(R.string.space_delete), color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showRenameDialog) {
        SpaceNameDialog(
            title = stringResource(R.string.space_rename_title),
            confirmLabel = stringResource(R.string.space_rename),
            initialName = space.name,
            enabled = canWrite,
            onDismiss = { showRenameDialog = false },
            onConfirm = { name ->
                onRenameSpace(space.id, name)
                showRenameDialog = false
            },
        )
    }

    pendingAction?.let { action ->
        ConfirmMemberActionDialog(
            action = action,
            confirmEnabled = canWrite && !state.isLoading,
            onDismiss = { pendingAction = null },
            onConfirm = {
                when (action.type) {
                    MemberActionType.Transfer -> {
                        onTransferOwnership(space.id, action.memberId)
                    }
                    MemberActionType.Remove -> onRemoveMember(space.id, action.memberId)
                    MemberActionType.Leave -> onLeaveSpace(space.id)
                }
                pendingAction = null
            },
        )
    }

    if (showDeleteSpaceDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSpaceDialog = false },
            title = { Text(stringResource(R.string.space_delete_title)) },
            text = { Text(stringResource(R.string.space_delete_body, space.name)) },
            confirmButton = {
                Button(enabled = canWrite && !state.isLoading, onClick = {
                    onDeleteSpace(space.id)
                    showDeleteSpaceDialog = false
                }) { Text(stringResource(R.string.space_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSpaceDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun OwnerInvitations(
    invitations: UiState<List<SpaceInvitation>>,
    spaceIsFull: Boolean,
    actionsEnabled: Boolean,
    onCreateInvitation: () -> Unit,
    onRevokeInvitation: (String) -> Unit,
    onShareInvitation: (SpaceInvitation) -> Unit,
) {
    Text(
        text = stringResource(R.string.invitation_section_title),
        style = MaterialTheme.typography.titleLarge,
    )
    Text(
        text = stringResource(R.string.invitation_section_description),
        style = MaterialTheme.typography.bodyMedium,
    )
    Button(
        onClick = onCreateInvitation,
        enabled = actionsEnabled && !spaceIsFull,
    ) {
        Text(text = stringResource(R.string.invitation_create))
    }
    if (spaceIsFull) {
        Text(
            text = stringResource(R.string.invitation_space_full_explanation),
            style = MaterialTheme.typography.bodySmall,
        )
    }

    when (invitations) {
        UiState.Loading -> CircularProgressIndicator()
        is UiState.Error -> Text(
            text = invitations.message ?: stringResource(R.string.generic_error),
            color = MaterialTheme.colorScheme.error,
        )
        is UiState.Content -> {
            if (invitations.value.isEmpty()) {
                Text(text = stringResource(R.string.invitation_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(invitations.value, key = SpaceInvitation::id) { invitation ->
                        InvitationCard(
                            invitation = invitation,
                            actionsEnabled = actionsEnabled,
                            onRevoke = { onRevokeInvitation(invitation.id) },
                            onShare = { onShareInvitation(invitation) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InvitationCard(
    invitation: SpaceInvitation,
    actionsEnabled: Boolean,
    onRevoke: () -> Unit,
    onShare: () -> Unit,
) {
    val expired = invitation.isExpired()
    val formatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
    }
    val clipboard = LocalClipboardManager.current
    var showQr by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(
                    if (expired) {
                        R.string.invitation_expired_label
                    } else {
                        R.string.invitation_active_label
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (expired) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = stringResource(R.string.invitation_code_value, invitation.code))
                    Text(text = invitation.link, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                text = stringResource(
                    R.string.invitation_expires_value,
                    formatter.format(invitation.expiresAt),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(invitation.code)) },
                    enabled = !expired,
                ) {
                    Text(text = stringResource(R.string.invitation_copy_code))
                }
                TextButton(onClick = { showQr = true }, enabled = !expired) {
                    Text(text = stringResource(R.string.invitation_show_qr))
                }
                TextButton(
                    onClick = onShare,
                    enabled = !expired,
                ) {
                    Text(text = stringResource(R.string.invitation_share))
                }
                TextButton(onClick = onRevoke, enabled = actionsEnabled) {
                    Text(text = stringResource(R.string.invitation_revoke))
                }
            }
        }
    }

    if (showQr) {
        InvitationQrDialog(link = invitation.link, onDismiss = { showQr = false })
    }
}

@Composable
private fun InvitationQrDialog(link: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.invitation_qr_title)) },
        text = {
            QrCodeImage(
                content = link,
                contentDescription = stringResource(R.string.invitation_qr_title),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        },
    )
}

@Composable
private fun MemberCard(
    member: SpaceMember,
    isCurrentUser: Boolean,
    canManage: Boolean,
    actionsEnabled: Boolean,
    onTransfer: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (isCurrentUser) {
                    stringResource(R.string.space_member_self, member.displayName)
                } else {
                    member.displayName
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (member.role == SpaceRole.OWNER) {
                        R.string.space_role_owner
                    } else {
                        R.string.space_role_member
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
            )
            if (canManage && member.role == SpaceRole.MEMBER) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onTransfer, enabled = actionsEnabled) {
                        Text(text = stringResource(R.string.space_transfer_ownership))
                    }
                    TextButton(onClick = onRemove, enabled = actionsEnabled) {
                        Text(text = stringResource(R.string.space_remove_member))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmMemberActionDialog(
    action: MemberAction,
    confirmEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val title = when (action.type) {
        MemberActionType.Transfer -> stringResource(R.string.space_transfer_confirm_title)
        MemberActionType.Remove -> stringResource(R.string.space_remove_confirm_title)
        MemberActionType.Leave -> stringResource(R.string.space_leave_confirm_title)
    }
    val body = when (action.type) {
        MemberActionType.Transfer -> stringResource(
            R.string.space_transfer_confirm_body,
            action.memberName,
        )
        MemberActionType.Remove -> stringResource(
            R.string.space_remove_confirm_body,
            action.memberName,
        )
        MemberActionType.Leave -> stringResource(R.string.space_leave_confirm_body)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = body) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(text = stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

private data class MemberAction(val type: MemberActionType, val memberId: String, val memberName: String)

private enum class MemberActionType {
    Transfer,
    Remove,
    Leave,
}
