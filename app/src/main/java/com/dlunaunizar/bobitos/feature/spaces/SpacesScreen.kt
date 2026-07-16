package com.dlunaunizar.bobitos.feature.spaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary

@Composable
fun SpacesScreen(
    state: UiState<List<SpaceSummary>>,
    managementState: SpaceManagementUiState,
    onSpaceSelected: (SpaceSummary) -> Unit,
    onCreateSpace: (String) -> Unit,
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
            onSpaceSelected = onSpaceSelected,
            onCreateSpace = onCreateSpace,
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
    onSpaceSelected: (SpaceSummary) -> Unit,
    onCreateSpace: (String) -> Unit,
    onProfileClick: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

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
            Text(
                text = stringResource(R.string.spaces_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            TextButton(onClick = onProfileClick) {
                Text(text = stringResource(R.string.profile_open))
            }
        }
        Text(
            text = stringResource(R.string.spaces_description),
            style = MaterialTheme.typography.bodyLarge,
        )
        SpaceFeedback(
            state = managementState,
            onDismiss = onClearFeedback,
        )
        Button(
            onClick = { showCreateDialog = true },
            enabled = !managementState.isLoading,
        ) {
            Text(text = stringResource(R.string.space_create))
        }

        if (spaces.isEmpty()) {
            Text(text = stringResource(R.string.spaces_empty))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(
                    items = spaces,
                    key = SpaceSummary::id,
                ) { space ->
                    SpaceCard(
                        space = space,
                        onClick = { onSpaceSelected(space) },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        SpaceNameDialog(
            title = stringResource(R.string.space_create_title),
            confirmLabel = stringResource(R.string.space_create),
            initialName = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreateSpace(name)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun SpaceCard(
    space: SpaceSummary,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = space.name,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(
                        if (space.role == SpaceRole.OWNER) {
                            R.string.space_role_owner
                        } else {
                            R.string.space_role_member
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = pluralStringResource(
                    id = R.plurals.space_members,
                    count = space.memberCount,
                    space.memberCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun SpaceNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
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
            TextButton(onClick = { onConfirm(name) }) {
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
private fun ErrorContent(
    modifier: Modifier = Modifier,
    message: String?,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message ?: stringResource(R.string.generic_error))
    }
}
