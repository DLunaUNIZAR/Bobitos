package com.dlunaunizar.bobitos.feature.routines

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.core.designsystem.component.RowPosition
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class RoutineCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val routine = Routine(
        id = "r1",
        ownerUid = "u1",
        visibility = RoutineVisibility.PRIVATE,
        title = "Pierna",
        createdBy = "u1",
        createdByName = "Ana",
        createdAt = Instant.parse("2026-10-17T10:00:00Z"),
        updatedBy = "u1",
        updatedAt = Instant.parse("2026-10-17T10:00:00Z"),
    )

    @Test fun deletingIsDisabledWhileAnotherWriteIsInFlight() {
        compose.setContent {
            BobitosTheme {
                RoutineCard(routine, RowPosition.SINGLE, canEdit = true, onOpen = {
                }, onEdit = {}, onDelete = {}, deleteEnabled = false)
            }
        }
        compose.onNodeWithContentDescription("Más opciones de «Pierna»").performClick()
        compose.onNodeWithText("Editar").assertIsEnabled()
        compose.onNodeWithText("Eliminar").assertIsNotEnabled()
    }
}
