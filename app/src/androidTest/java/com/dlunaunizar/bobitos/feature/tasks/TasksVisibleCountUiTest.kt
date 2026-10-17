package com.dlunaunizar.bobitos.feature.tasks

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Rule
import org.junit.Test

class TasksVisibleCountUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theWordFollowsTheTotalNotTheVisibleCount() {
        compose.setContent { BobitosTheme { TasksVisibleCount(visible = 1, total = 2) } }
        compose.onNodeWithText("1 de 2 tareas").assertIsDisplayed()
    }

    @Test
    fun zeroOfOneIsSingular() {
        compose.setContent { BobitosTheme { TasksVisibleCount(visible = 0, total = 1) } }
        compose.onNodeWithText("0 de 1 tarea").assertIsDisplayed()
    }
}
