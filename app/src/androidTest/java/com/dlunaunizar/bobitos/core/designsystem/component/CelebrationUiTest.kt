package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CelebrationUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun laCelebracionNoTapaLosToques() {
        var toques = 0
        compose.setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { toques++ }) { Text("Debajo") }
                Celebration(visible = true, message = "Todo hecho, enhorabuena a toda la casa", onFinished = {})
            }
        }
        compose.onNodeWithText("Todo hecho, enhorabuena a toda la casa").assertExists()
        compose.onNodeWithText("Debajo").performClick()
        assertEquals(1, toques)
    }
}
