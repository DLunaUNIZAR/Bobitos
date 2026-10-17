package com.dlunaunizar.bobitos.feature.sport

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.SportActivity
import com.dlunaunizar.bobitos.core.model.SportType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ActivityCardUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingTheCardOpensItsEditor() {
        var edits = 0
        compose.setContent {
            BobitosTheme {
                ActivityCard(
                    activity = SportActivity(
                        id = "a1",
                        date = LocalDate.of(2026, 7, 25),
                        type = SportType.PADEL,
                        name = "Pesas del sábado",
                        participantIds = emptyList(),
                        participantNames = emptyList(),
                        createdBy = "u1",
                        createdByName = "Ana",
                        createdAt = Instant.EPOCH,
                        updatedBy = "u1",
                        updatedAt = Instant.EPOCH,
                    ),
                    enabled = true,
                    canWrite = true,
                    onEdit = { edits++ },
                    onDelete = {},
                    onToggleDone = {},
                )
            }
        }

        compose.onNodeWithText("Pesas del sábado").performClick()

        assertEquals(1, edits)
    }
}
