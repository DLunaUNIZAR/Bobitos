package com.dlunaunizar.bobitos.feature.tasks

import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TaskOverdueTest {
    private val zone = ZoneId.of("UTC")
    private val due = LocalDate.of(2026, 10, 18).atTime(12, 0).atZone(zone).toInstant()

    private fun task(dueAt: Instant?) = TaskItem(
        id = "t1",
        title = "Tarea",
        description = null,
        assigneeId = null,
        assigneeName = null,
        dueAt = dueAt,
        priority = TaskPriority.MEDIUM,
        status = TaskStatus.TODO,
        createdBy = "u1",
        createdByName = "Ana",
        createdAt = Instant.EPOCH,
        updatedBy = "u1",
        updatedAt = Instant.EPOCH,
        completedBy = null,
        completedByName = null,
        completedAt = null,
    )

    @Test
    fun vencidaSoloSiSuDiaYaPasoRespectoAlHoyRecibido() {
        assertFalse(isOverdue(task(due), done = false, today = LocalDate.of(2026, 10, 18), zone = zone))
        assertTrue(isOverdue(task(due), done = false, today = LocalDate.of(2026, 10, 19), zone = zone))
    }

    @Test
    fun unaHechaOSinFechaNuncaEstaVencida() {
        assertFalse(isOverdue(task(due), done = true, today = LocalDate.of(2026, 10, 19), zone = zone))
        assertFalse(isOverdue(task(null), done = false, today = LocalDate.of(2026, 10, 19), zone = zone))
    }
}
