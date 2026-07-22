package com.dlunaunizar.bobitos

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.app.AppViewModel
import com.dlunaunizar.bobitos.app.BobitosApp
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.designsystem.theme.ThemeMode
import com.dlunaunizar.bobitos.core.model.InvitationCode
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.navigation.RecipeShareUrl
import com.dlunaunizar.bobitos.feature.auth.AuthViewModel
import com.dlunaunizar.bobitos.feature.auth.ThemeViewModel
import com.dlunaunizar.bobitos.feature.spaces.SpacesViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val spacesViewModel: SpacesViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()
    private val pendingInvitationCode = MutableStateFlow<String?>(null)
    private val pendingRecipeImportUrl = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingInvitationCode.value = InvitationCode.fromDeepLink(intent?.dataString)
        pendingRecipeImportUrl.value = recipeUrlFromShare(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val authActionState by authViewModel.uiState.collectAsStateWithLifecycle()
            val spaceManagementState by spacesViewModel.uiState.collectAsStateWithLifecycle()
            val invitationCode by pendingInvitationCode.collectAsStateWithLifecycle()
            val recipeImportUrl by pendingRecipeImportUrl.collectAsStateWithLifecycle()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(lightScrim, darkScrim) { darkTheme },
                )
                onDispose {}
            }

            BobitosTheme(darkTheme = darkTheme) {
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
                    pendingRecipeImportUrl = recipeImportUrl,
                    onRecipeImportUrlConsumed = { pendingRecipeImportUrl.value = null },
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
        recipeUrlFromShare(intent)?.let { pendingRecipeImportUrl.value = it }
    }

    // Enlace de receta compartido desde otra app (ACTION_SEND text/plain), o null si no aplica.
    private fun recipeUrlFromShare(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        return RecipeShareUrl.from(intent.getStringExtra(Intent.EXTRA_TEXT))
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

// Velos (scrims) de las barras del sistema en edge-to-edge, atados al tema de la app (no al móvil).
private val lightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val darkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
