package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScreenScaffoldTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun statusBarTopPx(): Int = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
        .getInsets(WindowInsetsCompat.Type.statusBars()).top

    @Test
    fun theTitleIsDrawnBelowTheStatusBar() {
        compose.setContent { BobitosTheme { ScreenScaffold(title = "Ajustes del espacio", onBack = {}) { Text("x") } } }
        val top = compose.onNodeWithText("Ajustes del espacio").fetchSemanticsNode().boundsInWindow.top
        assertTrue("title top $top < status bar ${statusBarTopPx()}", top >= statusBarTopPx())
    }

    @Test
    fun backIsAnIconButtonNotATextButton() {
        compose.setContent { BobitosTheme { ScreenScaffold(title = "T", onBack = {}) { Text("x") } } }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.navigate_back)).assertHasClickAction()
        compose.onAllNodesWithText(compose.activity.getString(R.string.navigate_back)).assertCountEquals(0)
    }

    @Test
    fun contentCanShowASnackbarThroughTheLocalHost() {
        compose.setContent {
            BobitosTheme {
                ScreenScaffold(title = "T", onBack = null) {
                    val host = LocalSnackbarHostState.current
                    LaunchedEffect(Unit) { host?.showSnackbar("Hola") }
                }
            }
        }
        compose.onNodeWithText("Hola").assertIsDisplayed()
    }
}
