package com.dlunaunizar.bobitos.feature.tasks

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TaskFilterBarUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var filters by mutableStateOf(TaskFilters())
    private val members = listOf(SpaceMember("u1", "Ana", SpaceRole.MEMBER))

    private fun show(fontScale: Float? = null) {
        compose.setContent {
            val base = LocalDensity.current
            val density = if (fontScale == null) base else Density(base.density, fontScale)
            CompositionLocalProvider(LocalDensity provides density) {
                BobitosTheme {
                    Box(Modifier.width(411.dp)) {
                        TaskFilterBar(filters, members, onChange = { filters = it })
                    }
                }
            }
        }
    }

    private fun pickInMenu(text: String) {
        compose.onNode(hasText(text) and hasAnyAncestor(isPopup())).performClick()
    }

    @Test fun theStatusDropdownShowsTheChosenValue() {
        show()
        compose.onNodeWithText("Estado").assertIsDisplayed()
        compose.onNodeWithText("Pendientes").performClick()
        pickInMenu("Completadas")
        compose.onNodeWithText("Completadas").assertIsDisplayed()
        assertEquals(TaskStatus.DONE, filters.status)
        compose.onNodeWithText("Completadas").performClick()
        pickInMenu("Todas")
        assertEquals(null, filters.status)
    }

    @Test fun thePriorityDropdownShowsTheChosenValue() {
        show()
        compose.onNodeWithText("Prioridad").assertIsDisplayed()
        compose.onNodeWithText("Todas").performClick()
        pickInMenu("Alta")
        compose.onNodeWithText("Alta").assertIsDisplayed()
        assertEquals(TaskPriority.HIGH, filters.priority)
    }

    @Test fun theAssigneeDropdownOffersUnassigned() {
        show()
        compose.onNodeWithText("Responsable").assertIsDisplayed()
        compose.onAllNodesWithText("Todos")[1].performClick() // el primero es Tipo
        pickInMenu("Sin asignar")
        assertTrue(filters.unassignedOnly)
    }

    @Test fun theMenuMarksTheCurrentOption() {
        filters = TaskFilters(priority = TaskPriority.HIGH)
        show()
        compose.onNodeWithText("Alta").performClick()
        compose.onNode(hasText("Alta") and hasAnyAncestor(isPopup()))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNode(hasText("Media") and hasAnyAncestor(isPopup()))
            .assert(SemanticsMatcher("not selected") { it.config.getOrNull(SemanticsProperties.Selected) != true })
    }

    @Test fun noChipLabelIsEllipsizedAtLargeFont() {
        show(fontScale = 1.3f)
        listOf("Estado", "Pendientes", "Prioridad", "Tipo", "Responsable").forEach { label ->
            val results = mutableListOf<TextLayoutResult>()
            val node = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
            assertNotNull("$label sin GetTextLayoutResult", action)
            action!!(results)
            assertFalse("$label cortada", results.first().isLineEllipsized(0))
        }
    }
}
