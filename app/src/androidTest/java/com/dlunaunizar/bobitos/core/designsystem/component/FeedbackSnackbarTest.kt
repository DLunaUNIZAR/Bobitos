package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FeedbackSnackbarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun anInfoMessageFloatsAndGoesAway() {
        var feedback by mutableStateOf<Feedback<String>?>(null)
        compose.setContent {
            BobitosTheme {
                ScreenScaffold(title = "T", onBack = null) { padding ->
                    LazyColumn(contentPadding = padding) { items((0 until 5).toList()) { Text("Fila $it") } }
                    FeedbackSnackbarEffect(feedback = feedback, onShown = { feedback = null })
                }
            }
        }
        val before = compose.onNodeWithText("Fila 0").fetchSemanticsNode().boundsInWindow.top
        compose.runOnIdle { feedback = Feedback("Producto añadido", false) }
        compose.onNodeWithText("Producto añadido").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithText("Fila 0").fetchSemanticsNode().boundsInWindow.top, 0f)
        compose.mainClock.advanceTimeBy(6_000)
        compose.onAllNodesWithText("Producto añadido").assertCountEquals(0)
    }
}
