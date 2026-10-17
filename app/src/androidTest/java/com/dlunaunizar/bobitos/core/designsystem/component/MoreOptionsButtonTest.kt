package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Rule
import org.junit.Test

class MoreOptionsButtonTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun itsDescriptionNamesTheItem() {
        compose.setContent { BobitosTheme { MoreOptionsButton("Leche", onClick = {}) } }
        compose.onNodeWithContentDescription("Más opciones de «Leche»").assertHasClickAction()
    }

    @Test
    fun aDisabledButtonIsDisabled() {
        compose.setContent { BobitosTheme { MoreOptionsButton("Leche", onClick = {}, enabled = false) } }
        compose.onNodeWithContentDescription("Más opciones de «Leche»").assertIsNotEnabled()
    }
}
