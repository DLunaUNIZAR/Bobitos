package com.dlunaunizar.bobitos.feature.tasks

import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `filters combine status priority and assignee`() {
        val tasks = listOf(
            task("hi-mine", "member", TaskPriority.HIGH, TaskStatus.TODO),
            task("low-mine", "member", TaskPriority.LOW, TaskStatus.TODO),
            task("hi-other", "other", TaskPriority.HIGH, TaskStatus.TODO),
            task("hi-done", "member", TaskPriority.HIGH, TaskStatus.DONE),
        )
        val filtered = TaskFilters(
            status = TaskStatus.TODO,
            priority = TaskPriority.HIGH,
            assigneeId = "member",
        ).apply(tasks)

        assertEquals(listOf("hi-mine"), filtered.map(TaskItem::id))
    }

    @Test
    fun `unassigned filter highlights tasks left without responsible`() {
        val tasks = listOf(task("assigned", "member"), task("orphan", null))
        val filtered = TaskFilters(status = null, unassignedOnly = true).apply(tasks)
        assertEquals(listOf("orphan"), filtered.map(TaskItem::id))
    }

    @Test
    fun `tasks group into ordered sections by due date and completion`() {
        val now = Instant.parse("2026-07-16T12:00:00Z")
        val zone = ZoneId.of("UTC")
        val tasks = listOf(
            task("overdue", "m", dueAt = "2026-07-15T00:00:00Z"),
            task("today", "m", dueAt = "2026-07-16T00:00:00Z"),
            task("future", "m", dueAt = "2026-07-20T00:00:00Z"),
            task("nodate", "m", dueAt = null),
            task("done", "m", status = TaskStatus.DONE, dueAt = "2026-07-15T00:00:00Z"),
        )

        val sections = tasks.groupIntoSections(now, zone)

        assertEquals(
            listOf(
                TaskSection.OVERDUE,
                TaskSection.TODAY,
                TaskSection.UPCOMING,
                TaskSection.NO_DATE,
                TaskSection.COMPLETED,
            ),
            sections.map { it.first },
        )
        assertEquals(listOf("overdue"), sections.first().second.map(TaskItem::id))
        // Una tarea vencida pero completada va a Completadas, no a Vencidas.
        assertEquals(listOf("done"), sections.last().second.map(TaskItem::id))
    }

    @Test
    fun `empty sections are omitted`() {
        val sections = listOf(task("nodate", "m", dueAt = null)).groupIntoSections()
        assertEquals(listOf(TaskSection.NO_DATE), sections.map { it.first })
    }

    @Test
    fun `search matches title description and assignee, and blank matches all`() {
        val chore = task("chore", "Ana").copy(title = "Fregar cocina", description = "con lavavajillas")

        assertTrue(chore.matchesQuery(""))
        assertTrue(chore.matchesQuery("  "))
        assertTrue(chore.matchesQuery("fregar"))
        assertTrue(chore.matchesQuery("LAVAVAJILLAS"))
        // Buscar por responsable (assigneeName == "Ana", ver el helper task()).
        assertTrue(chore.matchesQuery("ana"))
        assertFalse(chore.matchesQuery("bici"))
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
