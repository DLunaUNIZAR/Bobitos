package com.dlunaunizar.bobitos.feature.spaces

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.SaveFeedbackControls
import com.dlunaunizar.bobitos.core.common.SaveState
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.SyncStatus
import org.junit.Rule
import org.junit.Test

class SpacesSavingUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    // L372: con otra acción guardándose, el diálogo de crear no deja confirmar.
    @Test fun createIsDisabledWhileAnotherActionIsSaving() {
        compose.setContent {
            BobitosTheme {
                SpacesScreen(
                    state = UiState.Content(emptyList()),
                    managementState = SpaceManagementUiState(save = SaveState(running = 1)),
                    syncStatus = SyncStatus.ONLINE,
                    canWrite = true,
                    onSpaceSelected = {},
                    onCreateSpace = {},
                    onAcceptInvitation = {},
                    pendingInvitationCode = null,
                    onInvitationCodeConsumed = {},
                    onProfileClick = {},
                    feedback = NoOpFeedback,
                )
            }
        }
        compose.onNodeWithText(text(R.string.space_create), useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Casa")
        // Con otra acción en curso el botón del diálogo ya dice «Guardando…» y no deja confirmar.
        compose.onNode(hasText(text(R.string.write_saving)) and hasAnyAncestor(isDialog())).assertIsNotEnabled()
    }

    // M3: lo que guarda es otra acción (el editor sigue IDLE), así que el diálogo se puede cancelar.
    @Test fun createCanBeCancelledWhileAnotherActionIsSaving() {
        compose.setContent {
            BobitosTheme {
                SpacesScreen(
                    state = UiState.Content(emptyList()),
                    managementState = SpaceManagementUiState(save = SaveState(running = 1)),
                    syncStatus = SyncStatus.ONLINE,
                    canWrite = true,
                    onSpaceSelected = {},
                    onCreateSpace = {},
                    onAcceptInvitation = {},
                    pendingInvitationCode = null,
                    onInvitationCodeConsumed = {},
                    onProfileClick = {},
                    feedback = NoOpFeedback,
                )
            }
        }
        compose.onNodeWithText(text(R.string.space_create), useUnmergedTree = true).performClick()
        val cancel = compose.onNode(hasText(text(R.string.cancel)) and hasAnyAncestor(isDialog()))
        cancel.assertIsEnabled()
        cancel.performClick()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
}

private object NoOpFeedback : SaveFeedbackControls {
    override fun consumeError() = Unit

    override fun consumeNotice() = Unit

    override fun consumeErrorIf(shown: Any) = Unit

    override fun consumeNoticeIf(shown: Any) = Unit

    override fun consumeCreated() = Unit

    override fun resetEditor() = Unit

    override fun releaseEditorError() = Unit
}
