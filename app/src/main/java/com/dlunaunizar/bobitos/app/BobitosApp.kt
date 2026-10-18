package com.dlunaunizar.bobitos.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.FullScreenLoading
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.navigation.BobitosNavHost
import com.dlunaunizar.bobitos.feature.auth.AuthActionUiState
import com.dlunaunizar.bobitos.feature.auth.AuthNavHost
import com.dlunaunizar.bobitos.feature.auth.EmailVerificationScreen
import com.dlunaunizar.bobitos.feature.auth.WelcomeScreen
import com.dlunaunizar.bobitos.feature.auth.WelcomeViewModel
import com.dlunaunizar.bobitos.feature.betaupdate.BetaUpdateActions
import com.dlunaunizar.bobitos.feature.betaupdate.BetaUpdatePrompt
import com.dlunaunizar.bobitos.feature.betaupdate.BetaUpdateSheet
import com.dlunaunizar.bobitos.feature.betaupdate.BetaUpdateUiState
import com.dlunaunizar.bobitos.feature.reminders.RemindersViewModel
import com.dlunaunizar.bobitos.feature.spaces.SpaceManagementUiState

@Composable
fun BobitosApp(
    uiState: AppUiState,
    authActionState: AuthActionUiState,
    spaceManagementState: SpaceManagementUiState,
    onSpaceSelected: (String) -> Unit,
    onRetrySpaces: () -> Unit,
    onRealtimeScopeChanged: (RealtimeScope) -> Unit,
    onCreateSpace: (String) -> Unit,
    onObserveSpaceSettings: (String, Boolean) -> Unit,
    onStopObservingSpaceSettings: () -> Unit,
    onRenameSpace: (String, String) -> Unit,
    onLeaveSpace: (String) -> Unit,
    onRemoveMember: (String, String) -> Unit,
    onTransferOwnership: (String, String) -> Unit,
    onDeleteSpace: (String) -> Unit,
    onCreateInvitation: (String) -> Unit,
    onRevokeInvitation: (String) -> Unit,
    onAcceptInvitation: (String) -> Unit,
    onShareInvitation: (SpaceInvitation) -> Unit,
    onConsumeAcceptedSpace: () -> Unit,
    pendingInvitationCode: String?,
    onInvitationCodeConsumed: () -> Unit,
    pendingRecipeImportUrl: String?,
    onRecipeImportUrlConsumed: () -> Unit,
    onClearSpaceFeedback: () -> Unit,
    onEditorSaveConsumed: () -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onRegister: (
        displayName: String,
        email: String,
        password: String,
        passwordConfirmation: String,
    ) -> Unit,
    onPasswordReset: (email: String) -> Unit,
    onRefreshVerification: () -> Unit,
    onResendVerification: () -> Unit,
    onUpdateDisplayName: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: (String) -> Unit,
    onClearAuthFeedback: () -> Unit,
    betaUpdateState: BetaUpdateUiState,
    betaUpdateActions: BetaUpdateActions,
) {
    when (val authState = uiState.authUser) {
        UiState.Loading -> FullScreenLoading()
        is UiState.Error -> AppLoadError()
        is UiState.Content -> {
            val user = authState.value
            when {
                user == null -> key("unauthenticated") {
                    val welcomeViewModel: WelcomeViewModel = hiltViewModel()
                    val welcomeSeen by welcomeViewModel.seen.collectAsStateWithLifecycle()
                    when (welcomeSeen) {
                        null -> FullScreenLoading()
                        false -> WelcomeScreen(onStart = welcomeViewModel::markSeen)
                        true -> AuthNavHost(
                            navController = rememberNavController(),
                            actionState = authActionState,
                            onSignIn = onSignIn,
                            onRegister = onRegister,
                            onPasswordReset = onPasswordReset,
                            onClearFeedback = onClearAuthFeedback,
                        )
                    }
                }
                !user.isEmailVerified -> key("email-verification") {
                    EmailVerificationScreen(
                        user = user,
                        actionState = authActionState,
                        onRefreshVerification = onRefreshVerification,
                        onResendVerification = onResendVerification,
                        onSignOut = onSignOut,
                    )
                }
                else -> key("authenticated-${user.id}") {
                    when (uiState.spaces) {
                        UiState.Loading -> FullScreenLoading()
                        is UiState.Error -> AppLoadError()
                        is UiState.Content -> {
                            val remindersViewModel: RemindersViewModel = hiltViewModel()
                            val remindersEnabled by remindersViewModel.enabled.collectAsStateWithLifecycle()
                            val remindersLeadTime by remindersViewModel.leadTime.collectAsStateWithLifecycle()
                            LaunchedEffect(user.id, uiState.spaces, remindersEnabled, remindersLeadTime) {
                                remindersViewModel.sync(user.id, uiState.spaces.value, remindersEnabled)
                            }
                            BobitosNavHost(
                                navController = rememberNavController(),
                                uiState = uiState,
                                authUser = user,
                                authActionState = authActionState,
                                spaceManagementState = spaceManagementState,
                                onSpaceSelected = onSpaceSelected,
                                onRetrySpaces = onRetrySpaces,
                                onRealtimeScopeChanged = onRealtimeScopeChanged,
                                onCreateSpace = onCreateSpace,
                                onObserveSpaceSettings = onObserveSpaceSettings,
                                onStopObservingSpaceSettings = onStopObservingSpaceSettings,
                                onRenameSpace = onRenameSpace,
                                onLeaveSpace = onLeaveSpace,
                                onRemoveMember = onRemoveMember,
                                onTransferOwnership = onTransferOwnership,
                                onDeleteSpace = onDeleteSpace,
                                onCreateInvitation = onCreateInvitation,
                                onRevokeInvitation = onRevokeInvitation,
                                onAcceptInvitation = onAcceptInvitation,
                                onShareInvitation = onShareInvitation,
                                onConsumeAcceptedSpace = onConsumeAcceptedSpace,
                                pendingInvitationCode = pendingInvitationCode,
                                onInvitationCodeConsumed = onInvitationCodeConsumed,
                                pendingRecipeImportUrl = pendingRecipeImportUrl,
                                onRecipeImportUrlConsumed = onRecipeImportUrlConsumed,
                                onClearSpaceFeedback = onClearSpaceFeedback,
                                onEditorSaveConsumed = onEditorSaveConsumed,
                                onUpdateDisplayName = onUpdateDisplayName,
                                onSignOut = onSignOut,
                                onDeleteAccount = onDeleteAccount,
                                onClearAuthFeedback = onClearAuthFeedback,
                                betaUpdateState = betaUpdateState,
                                onCheckForBetaUpdate = betaUpdateActions::checkManually,
                            )
                        }
                    }
                }
            }
        }
    }
    betaUpdateState.prompt?.let { release ->
        BetaUpdatePrompt(
            release = release,
            onUpdate = betaUpdateActions::acceptPrompt,
            onLater = betaUpdateActions::postponePrompt,
            onDismiss = betaUpdateActions::dismissPrompt,
        )
    }
    betaUpdateState.sheet?.let { sheet ->
        BetaUpdateSheet(
            state = sheet,
            onStartDownload = betaUpdateActions::startDownload,
            onClose = betaUpdateActions::closeSheet,
        )
    }
}

// Error al arrancar o al leer los espacios: un texto fijo, nunca el de la excepción (técnico y en inglés).
@Composable
private fun AppLoadError() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(R.string.app_start_error))
    }
}
