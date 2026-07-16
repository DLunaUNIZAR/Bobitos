package com.dlunaunizar.bobitos

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
import com.dlunaunizar.bobitos.feature.auth.AuthViewModel
import com.dlunaunizar.bobitos.feature.spaces.SpacesViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val spacesViewModel: SpacesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val authActionState by authViewModel.uiState.collectAsStateWithLifecycle()
            val spaceManagementState by spacesViewModel.uiState.collectAsStateWithLifecycle()

            BobitosTheme {
                BobitosApp(
                    uiState = uiState,
                    authActionState = authActionState,
                    spaceManagementState = spaceManagementState,
                    onSpaceSelected = viewModel::selectSpace,
                    onCreateSpace = spacesViewModel::createSpace,
                    onObserveMembers = spacesViewModel::observeMembers,
                    onStopObservingMembers = spacesViewModel::stopObservingMembers,
                    onRenameSpace = spacesViewModel::renameSpace,
                    onLeaveSpace = spacesViewModel::leaveSpace,
                    onRemoveMember = spacesViewModel::removeMember,
                    onTransferOwnership = spacesViewModel::transferOwnership,
                    onClearSpaceFeedback = spacesViewModel::clearFeedback,
                    onSignIn = authViewModel::signIn,
                    onRegister = authViewModel::register,
                    onPasswordReset = authViewModel::sendPasswordReset,
                    onRefreshVerification = authViewModel::refreshEmailVerification,
                    onResendVerification = authViewModel::resendVerificationEmail,
                    onUpdateDisplayName = authViewModel::updateDisplayName,
                    onSignOut = authViewModel::signOut,
                    onClearAuthFeedback = authViewModel::clearFeedback,
                )
            }
        }
    }
}
