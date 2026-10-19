package com.dlunaunizar.bobitos.feature.tasks

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskStatus
import com.dlunaunizar.bobitos.feature.common.FakeTasks
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class TasksScreenCompletingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun task(id: String) = TaskItem(
        id = id,
        title = id,
        description = null,
        assigneeId = null,
        assigneeName = null,
        dueAt = null,
        priority = TaskPriority.MEDIUM,
        status = TaskStatus.TODO,
        createdBy = "ana",
        createdByName = "Ana",
        createdAt = Instant.parse("2026-01-01T10:00:00Z"),
        updatedBy = "ana",
        updatedAt = Instant.parse("2026-01-01T10:00:00Z"),
        completedBy = null,
        completedByName = null,
        completedAt = null,
    )

    @Test
    fun marcarTresTareasMuySeguidasEscribeLasTresAlMomento() {
        val fake = FakeTasks(listOf(task("barrer"), task("fregar"), task("polvo"), task("ropa")))
        val viewModel = fake.viewModel()
        compose.setContent { BobitosTheme { TasksScreen("space-1", canWrite = true, viewModel = viewModel) } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.onNode(hasContentDescription("barrer")).performClick()
        compose.mainClock.advanceTimeBy(60)
        compose.onNode(hasContentDescription("fregar")).performClick()
        compose.mainClock.advanceTimeBy(60)
        compose.onNode(hasContentDescription("polvo")).performClick()

        // Las tres escrituras salen antes de que el reloj llegue a los 450 ms, sin esperar a la anterior.
        assertEquals(listOf("barrer", "fregar", "polvo"), fake.completed)
        compose.onNodeWithText("barrer").assertExists()
        compose.onNodeWithText("fregar").assertExists()
        compose.onNodeWithText("polvo").assertExists()
        // Sin la barra de «Guardando».
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
    }
}
