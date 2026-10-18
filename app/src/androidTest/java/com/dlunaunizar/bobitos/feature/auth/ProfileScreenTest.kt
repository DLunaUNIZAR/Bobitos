package com.dlunaunizar.bobitos.feature.auth

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.designsystem.theme.ThemeMode
import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.SyncStatus
import com.dlunaunizar.bobitos.data.reminders.ReminderLeadTime
import com.dlunaunizar.bobitos.feature.betaupdate.BetaUpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun setProfile(
        themeMode: ThemeMode = ThemeMode.SYSTEM,
        onThemeModeChange: (ThemeMode) -> Unit = {},
        remindersEnabled: () -> Boolean = { false },
        onRemindersChange: (Boolean) -> Unit = {},
    ) {
        compose.setContent {
            BobitosTheme {
                ProfileContent(
                    user = AuthUser("user", "David", "david@example.com", true),
                    actionState = AuthActionUiState(), syncStatus = SyncStatus.ONLINE,
                    canWrite = true, onUpdateDisplayName = {}, onSignOut = {},
                    onDeleteAccount = {}, onBack = {}, onClearFeedback = {},
                    betaUpdateState = BetaUpdateUiState("0.1.0-beta.24", available = false, testerSignedIn = false),
                    onCheckForBetaUpdate = {},
                    themeMode = themeMode, onThemeModeChange = onThemeModeChange,
                    remindersEnabled = remindersEnabled(), onRemindersChange = onRemindersChange,
                    remindersLeadTime = ReminderLeadTime.AT_TIME, onRemindersLeadTimeChange = {},
                )
            }
        }
    }

    @Test fun privacyPolicyAndAccountDeletionAreReachable() {
        setProfile()
        compose.onNodeWithText("Política de privacidad").performScrollTo().performClick()
        compose.onNodeWithText("Bobitos usa tu correo", substring = true).assertIsDisplayed()
    }

    @Test fun themeAndRemindersReachTheirCallbacks() {
        val enabled = mutableStateOf(true)
        var theme: ThemeMode? = null
        var reminders: Boolean? = null
        setProfile(
            onThemeModeChange = { theme = it },
            remindersEnabled = { enabled.value },
            onRemindersChange = { reminders = it },
        )
        compose.onNodeWithText(string(R.string.settings_theme_dark)).performScrollTo().performClick()
        assertEquals(ThemeMode.DARK, theme)

        compose.onAllNodesWithText(string(R.string.profile_reminders_label))
            .filter(isToggleable()).onFirst().performScrollTo().performClick()
        assertEquals(false, reminders)

        compose.onNodeWithText(string(R.string.profile_reminders_lead_label)).performScrollTo().assertIsDisplayed()

        compose.runOnIdle { enabled.value = false }
        compose.onAllNodesWithText(string(R.string.profile_reminders_lead_label)).assertCountEquals(0)
    }
}
