package com.dlunaunizar.bobitos.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R

@Composable
fun LoginScreen(
    actionState: AuthActionUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onRegisterClick: () -> Unit,
    onPasswordResetClick: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    AuthScreenLayout(
        title = stringResource(R.string.auth_login_title),
        subtitle = stringResource(R.string.auth_login_subtitle),
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
                imeAction = ImeAction.Next,
            ),
            enabled = !actionState.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                onClearFeedback()
            },
            label = { Text(stringResource(R.string.auth_password_label)) },
            singleLine = true,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(
                        stringResource(
                            if (passwordVisible) {
                                R.string.auth_hide_password
                            } else {
                                R.string.auth_show_password
                            },
                        ),
                    )
                }
            },
            enabled = !actionState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
        TextButton(
            onClick = onPasswordResetClick,
            enabled = !actionState.isLoading,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.auth_forgot_password))
        }
        Button(
            onClick = { onSignIn(email, password) },
            enabled = !actionState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            if (actionState.isLoading) {
                CircularProgressIndicator()
            } else {
                Text(stringResource(R.string.auth_sign_in))
            }
        }
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.auth_no_account))
            TextButton(
                onClick = onRegisterClick,
                enabled = !actionState.isLoading,
            ) {
                Text(stringResource(R.string.auth_sign_up))
            }
        }
    }
}
