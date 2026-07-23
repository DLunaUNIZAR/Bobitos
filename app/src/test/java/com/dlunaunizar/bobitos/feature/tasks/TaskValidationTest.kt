package com.dlunaunizar.bobitos.feature.tasks

import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TaskValidationTest {
    @Test
    fun `title is required but the assignee is optional`() {
        assertEquals(TaskUiMessage.TitleRequired, TaskValidation.validate(" ", null))
        // Una tarea sin responsable es válida (responsable opcional).
        assertNull(TaskValidation.validate("Tarea", null))
    }

    @Test
    fun `field limits are validated`() {
        assertEquals(
            TaskUiMessage.TitleTooLong,
            TaskValidation.validate("a".repeat(121), null),
        )
        assertEquals(
            TaskUiMessage.DescriptionTooLong,
            TaskValidation.validate("Tarea", "a".repeat(1001)),
        )
    }

    @Test
    fun `ISO date is parsed in selected zone`() {
        assertEquals(
            Instant.parse("2026-07-20T00:00:00Z"),
            TaskValidation.parseDueDate("2026-07-20", ZoneId.of("UTC")),
        )
        assertNull(TaskValidation.parseDueDate(" ", ZoneId.of("UTC")))
    }

    @Test(expected = InvalidTaskDateException::class)
    fun `invalid date is rejected`() {
        TaskValidation.parseDueDate("20/07/2026")
    }

    @Test
    fun `filters combine status assignee priority and due date`() {
        val now = Instant.parse("2026-07-16T12:00:00Z")
        val tasks = listOf(
            task("overdue", "member", TaskPriority.HIGH, TaskStatus.TODO, "2026-07-15T00:00:00Z"),
            task("future", "member", TaskPriority.HIGH, TaskStatus.TODO, "2026-07-20T00:00:00Z"),
            task("done", "member", TaskPriority.HIGH, TaskStatus.DONE, "2026-07-15T00:00:00Z"),
        )
        val filtered = TaskFilters(
            status = TaskStatus.TODO,
            priority = TaskPriority.HIGH,
            assigneeId = "member",
            date = TaskDateFilter.OVERDUE,
        ).apply(tasks, now, ZoneId.of("UTC"))

        assertEquals(listOf("overdue"), filtered.map(TaskItem::id))
    }

    @Test
    fun `unassigned filter highlights tasks left without responsible`() {
        val tasks = listOf(task("assigned", "member"), task("orphan", null))
        val filtered = TaskFilters(status = null, unassignedOnly = true).apply(tasks)
        assertEquals(listOf("orphan"), filtered.map(TaskItem::id))
    }
}

private fun task(
    id: String,
    assigneeId: String?,
    priority: TaskPriority = TaskPriority.MEDIUM,
    status: TaskStatus = TaskStatus.TODO,
    dueAt: String? = null,
) = TaskItem(
    id = id,
    title = id,
    description = null,
    assigneeId = assigneeId,
    assigneeName = assigneeId,
    dueAt = dueAt?.let(Instant::parse),
    priority = priority,
    status = status,
    createdBy = "owner",
    createdByName = "Owner",
    createdAt = Instant.EPOCH,
    updatedBy = "owner",
    updatedAt = Instant.EPOCH,
    completedBy = null,
    completedByName = null,
    completedAt = null,
)
