package com.dlunaunizar.bobitos.feature.spaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary

@Composable
fun SpaceSettingsScreen(
    space: SpaceSummary,
    currentUserId: String,
    state: SpaceManagementUiState,
    onObserveMembers: (String) -> Unit,
    onStopObservingMembers: () -> Unit,
    onRenameSpace: (String, String) -> Unit,
    onLeaveSpace: (String) -> Unit,
    onRemoveMember: (String, String) -> Unit,
    onTransferOwnership: (String, String) -> Unit,
    onClearFeedback: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<MemberAction?>(null) }

    DisposableEffect(space.id) {
        onObserveMembers(space.id)
        onDispose(onStopObservingMembers)
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
                enabled = !state.isLoading,
            ) {
                Text(text = stringResource(R.string.space_rename))
            }
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
                        actionsEnabled = !state.isLoading,
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
                enabled = !state.isLoading,
            ) {
                Text(text = stringResource(R.string.space_leave))
            }
        } else {
            Text(
                text = stringResource(R.string.space_owner_leave_explanation),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (showRenameDialog) {
        SpaceNameDialog(
            title = stringResource(R.string.space_rename_title),
            confirmLabel = stringResource(R.string.space_rename),
            initialName = space.name,
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
            TextButton(onClick = onConfirm) {
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

private data class MemberAction(
    val type: MemberActionType,
    val memberId: String,
    val memberName: String,
)

private enum class MemberActionType {
    Transfer,
    Remove,
    Leave,
}
