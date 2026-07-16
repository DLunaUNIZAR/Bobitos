package com.dlunaunizar.bobitos.feature.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.AuthUser

@Composable
fun EmailVerificationScreen(
    user: AuthUser,
    actionState: AuthActionUiState,
    onRefreshVerification: () -> Unit,
    onResendVerification: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthScreenLayout(
        title = stringResource(R.string.auth_verify_title),
        subtitle = stringResource(R.string.auth_verify_subtitle, user.email),
        actionState = actionState,
        modifier = modifier,
    ) {
        AuthAvatar(
            initials = user.initials,
            modifier = Modifier
                .size(88.dp)
                .padding(bottom = 8.dp),
        )
        Button(
            onClick = onRefreshVerification,
            enabled = !actionState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            if (actionState.isLoading) {
                CircularProgressIndicator()
            } else {
                Text(stringResource(R.string.auth_verification_completed))
            }
        }
        OutlinedButton(
            onClick = onResendVerification,
            enabled = !actionState.isLoading &&
                actionState.verificationResendSecondsRemaining == 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            val secondsRemaining = actionState.verificationResendSecondsRemaining
            Text(
                text = if (secondsRemaining > 0) {
                    stringResource(
                        R.string.auth_resend_verification_wait,
                        secondsRemaining,
                    )
                } else {
                    stringResource(R.string.auth_resend_verification)
                },
            )
        }
        TextButton(
            onClick = onSignOut,
            enabled = !actionState.isLoading,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.auth_sign_out))
        }
    }
}
