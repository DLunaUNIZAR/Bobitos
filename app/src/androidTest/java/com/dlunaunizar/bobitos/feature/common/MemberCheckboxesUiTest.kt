package com.dlunaunizar.bobitos.feature.common

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MemberCheckboxesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingTheNameTogglesTheMember() {
        val toggles = mutableListOf<Pair<String, Boolean>>()
        compose.setContent {
            BobitosTheme {
                MemberCheckboxes(
                    label = "Participantes",
                    members = listOf(SpaceMember("ana", "Ana", SpaceRole.OWNER)),
                    selectedIds = emptyList(),
                    onToggle = { id, selected -> toggles += id to selected },
                )
            }
        }

        compose.onNodeWithText("Ana").performClick()

        assertEquals(listOf("ana" to true), toggles)
    }

    @Test
    fun everyRowIsATallEnoughTouchTarget() {
        compose.setContent {
            BobitosTheme {
                MemberCheckboxes(
                    label = "Participantes",
                    members = listOf(
                        SpaceMember("ana", "Ana", SpaceRole.OWNER),
                        SpaceMember("bo", "Bo", SpaceRole.MEMBER),
                    ),
                    selectedIds = emptyList(),
                    onToggle = { _, _ -> },
                )
            }
        }
        val rows = compose.onAllNodes(isToggleable())
        repeat(2) { rows[it].assertHeightIsAtLeast(48.dp) }
    }
}
