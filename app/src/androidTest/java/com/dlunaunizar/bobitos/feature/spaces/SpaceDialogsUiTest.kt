package com.dlunaunizar.bobitos.feature.spaces

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SpaceDialogsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private var confirmed = 0
    private var edited = 0

    private fun setNameDialog(serverError: String? = null) {
        compose.setContent {
            BobitosTheme {
                SpaceNameDialog(
                    title = "Nuevo espacio",
                    confirmLabel = "Crear",
                    initialName = "",
                    onDismiss = {},
                    onConfirm = { confirmed++ },
                    serverError = serverError,
                    onEdited = { edited++ },
                )
            }
        }
    }

    private fun setCodeDialog(serverError: String? = null) {
        compose.setContent {
            BobitosTheme {
                InvitationCodeDialog(
                    initialCode = "",
                    enabled = true,
                    saving = false,
                    serverError = serverError,
                    onEdited = { edited++ },
                    onDismiss = {},
                    onConfirm = { confirmed++ },
                )
            }
        }
    }

    @Test fun theNameDialogOpensWithoutErrors() {
        setNameDialog()
        compose.onAllNodesWithText(text(R.string.space_error_name_required)).assertCountEquals(0)
        compose.onNodeWithText("Crear").assertIsEnabled()
    }

    @Test fun submittingABlankNameShowsTheErrorAndFocusesTheField() {
        setNameDialog()
        compose.onNodeWithText("Crear").performClick()
        compose.onNodeWithText(text(R.string.space_error_name_required)).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsFocused()
        assertEquals(0, confirmed)
    }

    @Test fun theNameErrorGoesAwayWhenCorrected() {
        setNameDialog()
        compose.onNodeWithText("Crear").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Casa")
        compose.onAllNodesWithText(text(R.string.space_error_name_required)).assertCountEquals(0)
    }

    @Test fun aNameServerErrorClearsWhenEditing() {
        setNameDialog(serverError = "Sin permiso.")
        compose.onNodeWithText("Sin permiso.").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("Casa")
        assertEquals(true, edited > 0)
    }

    @Test fun theCodeDialogOpensWithoutErrors() {
        setCodeDialog()
        compose.onAllNodesWithText(text(R.string.invitation_error_invalid_code)).assertCountEquals(0)
        compose.onNodeWithText(text(R.string.invitation_join_confirm)).assertIsEnabled()
    }

    @Test fun submittingAnInvalidCodeShowsTheErrorAndFocusesTheField() {
        setCodeDialog()
        compose.onNode(hasSetTextAction()).performTextInput("abc")
        compose.onNodeWithText(text(R.string.invitation_join_confirm)).performClick()
        compose.onNodeWithText(text(R.string.invitation_error_invalid_code)).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsFocused()
        assertEquals(0, confirmed)
    }

    @Test fun theCodeErrorGoesAwayWhenCorrected() {
        setCodeDialog()
        compose.onNode(hasSetTextAction()).performTextInput("abc")
        compose.onNodeWithText(text(R.string.invitation_join_confirm)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567")
        compose.onAllNodesWithText(text(R.string.invitation_error_invalid_code)).assertCountEquals(0)
    }

    @Test fun aCodeServerErrorClearsWhenEditing() {
        setCodeDialog(serverError = "Sin permiso.")
        compose.onNodeWithText("Sin permiso.").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("abc")
        assertEquals(true, edited > 0)
    }
}
