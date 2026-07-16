package com.dlunaunizar.bobitos.feature.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.SyncStatus
import org.junit.Rule
import org.junit.Test

class ProfileScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun privacyPolicyAndAccountDeletionAreReachable() {
        compose.setContent {
            BobitosTheme {
                ProfileScreen(
                    user = AuthUser("user", "David", "david@example.com", true),
                    actionState = AuthActionUiState(), syncStatus = SyncStatus.ONLINE,
                    canWrite = true, onUpdateDisplayName = {}, onSignOut = {},
                    onDeleteAccount = {}, onBack = {}, onClearFeedback = {},
                )
            }
        }
        compose.onNodeWithText("Política de privacidad").performScrollTo().performClick()
        compose.onNodeWithText("Bobitos usa tu correo", substring = true).assertIsDisplayed()
    }
}
