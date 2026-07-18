package com.dlunaunizar.bobitos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.app.AppViewModel
import com.dlunaunizar.bobitos.app.BobitosApp
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.InvitationCode
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.feature.auth.AuthViewModel
import com.dlunaunizar.bobitos.feature.spaces.SpacesViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val spacesViewModel: SpacesViewModel by viewModels()
    private val pendingInvitationCode = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingInvitationCode.value = InvitationCode.fromDeepLink(intent?.dataString)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val authActionState by authViewModel.uiState.collectAsStateWithLifecycle()
            val spaceManagementState by spacesViewModel.uiState.collectAsStateWithLifecycle()
            val invitationCode by pendingInvitationCode.collectAsStateWithLifecycle()

            BobitosTheme {
                BobitosApp(
                    uiState = uiState,
                    authActionState = authActionState,
                    spaceManagementState = spaceManagementState,
                    onSpaceSelected = viewModel::selectSpace,
                    onRealtimeScopeChanged = viewModel::setRealtimeScope,
                    onCreateSpace = spacesViewModel::createSpace,
                    onObserveSpaceSettings = spacesViewModel::observeSpaceSettings,
                    onStopObservingSpaceSettings = spacesViewModel::stopObservingSpaceSettings,
                    onRenameSpace = spacesViewModel::renameSpace,
                    onLeaveSpace = spacesViewModel::leaveSpace,
                    onRemoveMember = spacesViewModel::removeMember,
                    onTransferOwnership = spacesViewModel::transferOwnership,
                    onDeleteSpace = spacesViewModel::deleteSpace,
                    onCreateInvitation = spacesViewModel::createInvitation,
                    onRevokeInvitation = spacesViewModel::revokeInvitation,
                    onAcceptInvitation = spacesViewModel::acceptInvitation,
                    onShareInvitation = ::shareInvitation,
                    onConsumeAcceptedSpace = spacesViewModel::consumeAcceptedSpace,
                    pendingInvitationCode = invitationCode,
                    onInvitationCodeConsumed = { pendingInvitationCode.value = null },
                    onClearSpaceFeedback = spacesViewModel::clearFeedback,
                    onSignIn = authViewModel::signIn,
                    onRegister = authViewModel::register,
                    onPasswordReset = authViewModel::sendPasswordReset,
                    onRefreshVerification = authViewModel::refreshEmailVerification,
                    onResendVerification = authViewModel::resendVerificationEmail,
                    onUpdateDisplayName = authViewModel::updateDisplayName,
                    onSignOut = authViewModel::signOut,
                    onDeleteAccount = authViewModel::deleteAccount,
                    onClearAuthFeedback = authViewModel::clearFeedback,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingInvitationCode.value = InvitationCode.fromDeepLink(intent.dataString)
    }

    private fun shareInvitation(invitation: SpaceInvitation) {
        val message = getString(
            R.string.invitation_share_message,
            invitation.code,
            invitation.link,
        )
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, message)
        startActivity(Intent.createChooser(intent, getString(R.string.invitation_share)))
    }
}
