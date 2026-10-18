package com.dlunaunizar.bobitos.feature.tasks

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskStatus
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TaskMetaRowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun task(priority: TaskPriority, dueAt: Instant? = null) = TaskItem(
        id = "t1",
        title = "Tarea",
        description = null,
        assigneeId = "u1",
        assigneeName = "David Demo",
        dueAt = dueAt,
        priority = priority,
        status = TaskStatus.TODO,
        createdBy = "u1",
        createdByName = "David Demo",
        createdAt = Instant.EPOCH,
        updatedBy = "u1",
        updatedAt = Instant.EPOCH,
        completedBy = null,
        completedByName = null,
        completedAt = null,
    )

    private fun show(priority: TaskPriority) {
        compose.setContent {
            BobitosTheme { TaskMetaRow(task(priority), overdue = false, done = false, today = LocalDate.now()) }
        }
    }

    @Test fun mediumPriorityShowsItsName() {
        show(TaskPriority.MEDIUM)
        compose.onNodeWithText("Media").assertExists()
    }

    @Test fun highPriorityShowsItsName() {
        show(TaskPriority.HIGH)
        compose.onNodeWithText("Alta").assertExists()
    }

    private val zone = ZoneId.systemDefault()
    private val vencePorLaTarde = LocalDate.of(2026, 10, 18).atTime(12, 0).atZone(zone).toInstant()

    @Test fun elChipHoyDependeDelHoyQueSePasa() {
        compose.setContent {
            BobitosTheme {
                TaskMetaRow(
                    task(TaskPriority.MEDIUM, vencePorLaTarde),
                    overdue = false,
                    done = false,
                    today = LocalDate.of(2026, 10, 18),
                )
            }
        }
        compose.onNodeWithText("Hoy").assertExists()
    }

    @Test fun conOtroHoyElChipNoDiceHoy() {
        compose.setContent {
            BobitosTheme {
                TaskMetaRow(
                    task(TaskPriority.MEDIUM, vencePorLaTarde),
                    overdue = false,
                    done = false,
                    today = LocalDate.of(2026, 10, 19),
                )
            }
        }
        compose.onNodeWithText("Hoy").assertDoesNotExist()
    }
}
