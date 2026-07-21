package com.dlunaunizar.bobitos.feature.spaces

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.core.model.SyncStatus
import com.dlunaunizar.bobitos.feature.common.SyncStatusBanner

@Composable
fun SpacesScreen(
    state: UiState<List<SpaceSummary>>,
    managementState: SpaceManagementUiState,
    syncStatus: SyncStatus,
    canWrite: Boolean,
    onSpaceSelected: (SpaceSummary) -> Unit,
    onCreateSpace: (String) -> Unit,
    onAcceptInvitation: (String) -> Unit,
    pendingInvitationCode: String?,
    onInvitationCodeConsumed: () -> Unit,
    onProfileClick: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        UiState.Loading -> LoadingContent(modifier)
        is UiState.Error -> ErrorContent(modifier, state.message)
        is UiState.Content -> SpacesContent(
            spaces = state.value,
            managementState = managementState,
            syncStatus = syncStatus,
            canWrite = canWrite,
            onSpaceSelected = onSpaceSelected,
            onCreateSpace = onCreateSpace,
            onAcceptInvitation = onAcceptInvitation,
            pendingInvitationCode = pendingInvitationCode,
            onInvitationCodeConsumed = onInvitationCodeConsumed,
            onProfileClick = onProfileClick,
            onClearFeedback = onClearFeedback,
            modifier = modifier,
        )
    }
}

@Composable
private fun SpacesContent(
    spaces: List<SpaceSummary>,
    managementState: SpaceManagementUiState,
    syncStatus: SyncStatus,
    canWrite: Boolean,
    onSpaceSelected: (SpaceSummary) -> Unit,
    onCreateSpace: (String) -> Unit,
    onAcceptInvitation: (String) -> Unit,
    pendingInvitationCode: String?,
    onInvitationCodeConsumed: () -> Unit,
    onProfileClick: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var showJoinDialog by rememberSaveable { mutableStateOf(false) }
    var invitationCode by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(pendingInvitationCode) {
        pendingInvitationCode?.let { code ->
            invitationCode = code
            showJoinDialog = true
            onInvitationCodeConsumed()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.spaces_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                IconButton(onClick = onProfileClick) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = stringResource(R.string.profile_open),
                    )
                }
            }
            Text(
                text = stringResource(R.string.spaces_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SyncStatusBanner(status = syncStatus)
            SpaceFeedback(state = managementState, onDismiss = onClearFeedback)
            OutlinedButton(
                onClick = {
                    invitationCode = ""
                    showJoinDialog = true
                },
                enabled = !managementState.isLoading && canWrite,
            ) {
                Text(text = stringResource(R.string.invitation_join))
            }

            if (spaces.isEmpty()) {
                SpacesEmptyState(
                    enabled = !managementState.isLoading && canWrite,
                    onCreate = { showCreateDialog = true },
                    onJoin = {
                        invitationCode = ""
                        showJoinDialog = true
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(items = spaces, key = SpaceSummary::id) { space ->
                        SpaceCard(space = space, onClick = { onSpaceSelected(space) })
                    }
                }
            }
        }

        if (canWrite) {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.space_create)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }

    if (showCreateDialog) {
        SpaceNameDialog(
            title = stringResource(R.string.space_create_title),
            confirmLabel = stringResource(R.string.space_create),
            initialName = "",
            enabled = canWrite,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreateSpace(name)
                showCreateDialog = false
            },
        )
    }

    if (showJoinDialog) {
        InvitationCodeDialog(
            initialCode = invitationCode,
            enabled = canWrite,
            onDismiss = { showJoinDialog = false },
            onConfirm = { code ->
                onAcceptInvitation(code)
                showJoinDialog = false
            },
        )
    }
}

@Composable
private fun SpacesEmptyState(
    enabled: Boolean,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            icon = Icons.Rounded.Groups,
            title = stringResource(R.string.spaces_empty),
            description = stringResource(R.string.spaces_empty_hint),
            actionLabel = if (enabled) stringResource(R.string.space_create) else null,
            onAction = onCreate.takeIf { enabled },
        )
        TextButton(onClick = onJoin, enabled = enabled) {
            Text(stringResource(R.string.invitation_join))
        }
    }
}

@Composable
private fun InvitationCodeDialog(
    initialCode: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var code by rememberSaveable(initialCode) { mutableStateOf(initialCode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.invitation_join_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.invitation_join_description))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(text = stringResource(R.string.invitation_code_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(code) }, enabled = enabled) {
                Text(text = stringResource(R.string.invitation_join_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SpaceCard(space: SpaceSummary, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    val (container, content) = palette[space.id.hashCode().mod(palette.size)]
    val roleText = stringResource(
        if (space.role == SpaceRole.OWNER) R.string.space_role_owner else R.string.space_role_member,
    )
    val membersText = pluralStringResource(R.plurals.space_members, space.memberCount, space.memberCount)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonogramAvatar(
                name = space.name,
                containerColor = container,
                contentColor = content,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = space.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "$roleText · $membersText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonogramAvatar(name: String, containerColor: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(name),
            color = contentColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun initialsOf(name: String): String = name.trim()
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifEmpty { "?" }

@Composable
internal fun SpaceNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    enabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(R.string.space_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = enabled) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.generic_loading),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ErrorContent(modifier: Modifier = Modifier, message: String?) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message ?: stringResource(R.string.generic_error))
    }
}
