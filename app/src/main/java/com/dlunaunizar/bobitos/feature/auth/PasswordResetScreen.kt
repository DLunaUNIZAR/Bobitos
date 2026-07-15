package com.dlunaunizar.bobitos.feature.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R

@Composable
fun PasswordResetScreen(
    actionState: AuthActionUiState,
    onPasswordReset: (String) -> Unit,
    onBackToLogin: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }

    AuthScreenLayout(
        title = stringResource(R.string.auth_reset_title),
        subtitle = stringResource(R.string.auth_reset_subtitle),
        actionState = actionState,
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                onClearFeedback()
            },
            label = { Text(stringResource(R.string.auth_email_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            ),
            enabled = !actionState.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onPasswordReset(email) },
            enabled = !actionState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            if (actionState.isLoading) {
                CircularProgressIndicator()
            } else {
                Text(stringResource(R.string.auth_send_reset))
            }
        }
        TextButton(
            onClick = onBackToLogin,
            enabled = !actionState.isLoading,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.auth_back_to_login))
        }
    }
}
