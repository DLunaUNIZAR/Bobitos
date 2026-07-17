package com.dlunaunizar.bobitos.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.SyncStatus
import com.dlunaunizar.bobitos.feature.common.SyncStatusBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: AuthUser,
    actionState: AuthActionUiState,
    syncStatus: SyncStatus,
    canWrite: Boolean,
    onUpdateDisplayName: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: (String) -> Unit,
    onBack: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayName by rememberSaveable(user.id) {
        mutableStateOf(user.displayName)
    }
    var showDeleteAccount by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var deletionPassword by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(user.displayName) {
        displayName = user.displayName
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.profile_title)) },
                    navigationIcon = {
                        TextButton(onClick = onBack) {
                            Text(stringResource(R.string.navigate_back))
                        }
                    },
                )
                SyncStatusBanner(status = syncStatus)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AuthAvatar(
                initials = user.initials,
                modifier = Modifier.size(96.dp),
            )
            Text(
                text = stringResource(
                    R.string.profile_email_value,
                    user.email,
                ),
            )
            Text(text = stringResource(R.string.profile_verified))
            AuthFeedback(actionState)
            OutlinedTextField(
                value = displayName,
                onValueChange = {
                    displayName = it
                    onClearFeedback()
                },
                label = { Text(stringResource(R.string.auth_display_name_label)) },
                singleLine = true,
                enabled = !actionState.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onUpdateDisplayName(displayName) },
                enabled = !actionState.isLoading && canWrite,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (actionState.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(R.string.profile_save))
                }
            }
            OutlinedButton(
                onClick = onSignOut,
                enabled = !actionState.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.auth_sign_out))
            }
            TextButton(onClick = { showPrivacy = true }) { Text(stringResource(R.string.privacy_policy_title)) }
            TextButton(enabled = !actionState.isLoading && canWrite, onClick = { showDeleteAccount = true }) {
                Text(stringResource(R.string.account_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (showDeleteAccount) {
        AlertDialog(
            onDismissRequest = { showDeleteAccount = false },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.account_delete_body))
                    OutlinedTextField(
                        deletionPassword,
                        {
                            deletionPassword = it
                            onClearFeedback()
                        },
                        label = { Text(stringResource(R.string.auth_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(enabled = deletionPassword.isNotBlank() && !actionState.isLoading && canWrite, onClick = {
                    onDeleteAccount(deletionPassword)
                }) { Text(stringResource(R.string.account_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccount = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text(stringResource(R.string.privacy_policy_title)) },
            text = { Text(stringResource(R.string.privacy_policy_summary)) },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text(stringResource(R.string.dismiss)) }
            },
        )
    }
}
