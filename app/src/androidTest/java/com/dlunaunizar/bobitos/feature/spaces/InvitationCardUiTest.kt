package com.dlunaunizar.bobitos.feature.spaces

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.InvitationStatus
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class InvitationCardUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private var revoked = 0

    private fun setCard() {
        val invitation = SpaceInvitation(
            id = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567",
            spaceId = "s1",
            expiresAt = Instant.now().plusSeconds(3600),
            status = InvitationStatus.ACTIVE,
        )
        compose.setContent {
            BobitosTheme {
                InvitationCard(invitation = invitation, actionsEnabled = true, onRevoke = { revoked++ }, onShare = {})
            }
        }
    }

    @Test fun revokingAsksFirst() {
        setCard()
        compose.onNodeWithText(text(R.string.invitation_revoke)).performClick()
        compose.onNodeWithText(text(R.string.invitation_revoke_title)).assertIsDisplayed()
        assertEquals(0, revoked)
    }

    @Test fun confirmingRevokesOnce() {
        setCard()
        compose.onNodeWithText(text(R.string.invitation_revoke)).performClick()
        compose.onAllNodesWithText(text(R.string.invitation_revoke))[1].performClick()
        compose.waitForIdle()
        assertEquals(1, revoked)
    }

    @Test fun cancellingDoesNotRevoke() {
        setCard()
        compose.onNodeWithText(text(R.string.invitation_revoke)).performClick()
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        assertEquals(0, revoked)
    }
}
