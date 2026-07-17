package com.dlunaunizar.bobitos.feature.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R

@Composable
internal fun AuthScreenLayout(
    title: String,
    subtitle: String,
    actionState: AuthActionUiState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(PaddingValues(horizontal = 24.dp, vertical = 32.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = title,
            modifier = Modifier.padding(top = 28.dp),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AuthFeedback(actionState)
        content()
    }
}

@Composable
internal fun AuthFeedback(actionState: AuthActionUiState, modifier: Modifier = Modifier) {
    val message = actionState.error ?: actionState.notice ?: return
    val isError = actionState.error != null
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Text(
            text = stringResource(message.stringRes),
            modifier = Modifier.padding(16.dp),
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}

@Composable
internal fun AuthAvatar(initials: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun FullScreenLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@get:StringRes
internal val AuthUiMessage.stringRes: Int
    get() = when (this) {
        AuthUiMessage.DisplayNameRequired -> R.string.auth_error_display_name_required
        AuthUiMessage.DisplayNameTooLong -> R.string.auth_error_display_name_too_long
        AuthUiMessage.InvalidEmail -> R.string.auth_error_invalid_email
        AuthUiMessage.PasswordTooShort -> R.string.auth_error_password_too_short
        AuthUiMessage.PasswordsDoNotMatch -> R.string.auth_error_passwords_do_not_match
        AuthUiMessage.EmailAlreadyInUse -> R.string.auth_error_email_in_use
        AuthUiMessage.InvalidCredentials -> R.string.auth_error_invalid_credentials
        AuthUiMessage.NetworkError -> R.string.auth_error_network
        AuthUiMessage.TooManyRequests -> R.string.auth_error_too_many_requests
        AuthUiMessage.UnexpectedError -> R.string.auth_error_unexpected
        AuthUiMessage.VerificationEmailSent -> R.string.auth_notice_verification_sent
        AuthUiMessage.EmailStillNotVerified -> R.string.auth_notice_not_verified
        AuthUiMessage.PasswordResetRequested -> R.string.auth_notice_reset_requested
        AuthUiMessage.ProfileUpdated -> R.string.auth_notice_profile_updated
        AuthUiMessage.PasswordRequired -> R.string.account_error_password_required
        AuthUiMessage.OwnerSpacesRemaining -> R.string.account_error_owner_spaces
        AuthUiMessage.AccountDeleted -> R.string.account_notice_deleted
    }
