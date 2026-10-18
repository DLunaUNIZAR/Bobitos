package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.RecurrenceUnit
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskRecurrence
import com.dlunaunizar.bobitos.core.model.TaskStatus
import com.dlunaunizar.bobitos.core.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class TaskCompletionUndoTest {
    private val me = "yo"
    private val ayer = Instant.parse("2026-10-17T10:00:00Z")
    private val hoy = Instant.parse("2026-10-18T10:00:00Z")

    private val antes = TaskItem(
        id = "t1",
        title = "Regar",
        description = "Las macetas",
        assigneeId = "ana",
        assigneeName = "Ana",
        dueAt = ayer,
        priority = TaskPriority.MEDIUM,
        status = TaskStatus.TODO,
        createdBy = "ana",
        createdByName = "Ana",
        createdAt = ayer,
        updatedBy = "ana",
        updatedAt = ayer,
        completedBy = null,
        completedByName = null,
        completedAt = null,
        type = TaskType.HOGAR,
    )
    private val hecha = antes.copy(
        status = TaskStatus.DONE,
        updatedBy = me,
        updatedAt = hoy,
        completedBy = me,
        completedByName = "Yo",
        completedAt = hoy,
    )
    private val semanal = TaskRecurrence(RecurrenceUnit.WEEK, 1)
    private val repetidaAntes = antes.copy(recurrence = semanal, startAt = ayer)
    private val repetidaDespues = repetidaAntes.copy(
        startAt = hoy,
        dueAt = hoy,
        updatedBy = me,
        updatedAt = hoy,
    )

    @Test
    fun sinTocarSePuedeDeshacer() {
        assertNull(completionUndoFailure(hecha, antes, me))
        assertNull(completionUndoFailure(repetidaDespues, repetidaAntes, me))
    }

    @Test
    fun otroMiembroLaTocoNoSeDeshace() {
        assertEquals(TaskFailure.TaskChanged, completionUndoFailure(hecha.copy(updatedBy = "ana"), antes, me))
        assertEquals(
            TaskFailure.TaskChanged,
            completionUndoFailure(repetidaDespues.copy(updatedBy = "ana"), repetidaAntes, me),
        )
    }

    @Test
    fun unaNormalReabiertaNoSeDeshace() {
        assertEquals(
            TaskFailure.TaskChanged,
            completionUndoFailure(hecha.copy(status = TaskStatus.TODO), antes, me),
        )
    }

    @Test
    fun unaRepetidaQueYaNoEstaPendienteNoSeDeshace() {
        assertEquals(
            TaskFailure.TaskChanged,
            completionUndoFailure(repetidaDespues.copy(status = TaskStatus.DONE), repetidaAntes, me),
        )
    }

    @Test
    fun unCambioDeTituloNoSeDeshace() {
        assertEquals(TaskFailure.TaskChanged, completionUndoFailure(hecha.copy(title = "Fregar"), antes, me))
        assertEquals(TaskFailure.TaskChanged, completionUndoFailure(hecha.copy(description = null), antes, me))
        assertEquals(TaskFailure.TaskChanged, completionUndoFailure(hecha.copy(assigneeId = null), antes, me))
        assertEquals(TaskFailure.TaskChanged, completionUndoFailure(hecha.copy(type = TaskType.OTROS), antes, me))
    }

    @Test
    fun unCambioDePrioridadNoSeDeshace() {
        assertEquals(
            TaskFailure.TaskChanged,
            completionUndoFailure(hecha.copy(priority = TaskPriority.HIGH), antes, me),
        )
    }

    @Test
    fun unCambioDeRepeticionNoSeDeshace() {
        val diaria = TaskRecurrence(RecurrenceUnit.DAY, 1)
        assertEquals(
            TaskFailure.TaskChanged,
            completionUndoFailure(repetidaDespues.copy(recurrence = diaria), repetidaAntes, me),
        )
        assertEquals(
            TaskFailure.TaskChanged,
            completionUndoFailure(repetidaDespues.copy(recurrence = null), repetidaAntes, me),
        )
    }
}
