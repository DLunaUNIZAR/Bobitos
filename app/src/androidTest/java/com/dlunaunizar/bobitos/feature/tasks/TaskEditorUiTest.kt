package com.dlunaunizar.bobitos.feature.tasks

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Rule
import org.junit.Test

class TaskEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private fun setEditor(restoration: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            BobitosTheme {
                TaskEditor(
                    task = null,
                    template = null,
                    members = emptyList(),
                    saving = false,
                    errorMessage = null,
                    onDismiss = {},
                    onInvalidDate = {},
                    onSave = { _, _, _, _, _, _, _, _ -> },
                )
            }
        }
        if (restoration != null) restoration.setContent(content) else compose.setContent(content)
    }

    @Test fun opensWithoutErrors() {
        setEditor()
        compose.onAllNodesWithText(text(R.string.tasks_error_title_required)).assertCountEquals(0)
        compose.onNodeWithText(text(R.string.save)).assertIsEnabled()
    }

    @Test fun showsTheErrorUnderTheTitleAfterSaving() {
        setEditor()
        compose.onNodeWithText(text(R.string.save)).performClick()
        compose.onNodeWithText(text(R.string.tasks_error_title_required)).assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.tasks_title_label))).performTextInput("Limpiar")
        compose.onAllNodesWithText(text(R.string.tasks_error_title_required)).assertCountEquals(0)
    }

    @Test fun theAttemptSurvivesRotation() {
        val restoration = StateRestorationTester(compose)
        setEditor(restoration)
        compose.onNodeWithText(text(R.string.save)).performClick()
        compose.onNodeWithText(text(R.string.tasks_error_title_required)).assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText(text(R.string.tasks_error_title_required)).assertIsDisplayed()
    }
}
