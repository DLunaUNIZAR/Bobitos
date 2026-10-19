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
        assertNull(completionUndoFailure(hecha, hecha, me))
        assertNull(completionUndoFailure(repetidaDespues, repetidaDespues, me))
    }

    @Test
    fun otroMiembroLaTocoNoSeDeshace() {
        assertEquals(TaskFailure.TaskChanged, completionUndoFailure(hecha.copy(updatedBy = "ana"), hecha, me))
        assertEquals(
            TaskFailure.TaskChanged,
            completionUndoFailure(repetidaDespues.copy(updatedBy = "ana"), repetidaDespues, me),
        )
    }

    @Test
    fun unaNormalReabiertaNoSeDeshace() {
        assertEquals(
            TaskFailure.TaskChanged,
            completionUndoFailure(hecha.copy(status = TaskStatus.TODO), hecha, me),
        )
    }

    @Test
    fun unaRepetidaQueYaNoEstaPendienteNoSeDeshace() {
        assertEquals(
            TaskFailure.TaskChanged,
            completionUndoFailure(repetidaDespues.copy(status = TaskStatus.DONE), repetidaDespues, me),
        )
    }

    @Test
    fun unCambioDeTituloNoSeDeshace() {
        assertEquals(TaskFailure.TaskChanged, completionUndoFailure(hecha.copy(title = "Fregar"), hecha, me))
        assertEquals(TaskFailure.TaskChanged, completionUndoFailure(hecha.copy(description = null), hecha, me))
        assertEquals(TaskFailure.TaskChanged, completionUndoFailure(hecha.copy(assigneeId = null), hecha, me))
        assertEquals(TaskFailure.TaskChanged, completionUndoFailure(hecha.copy(type = TaskType.OTROS), hecha, me))
    }

    @Test
    fun unCambioDePrioridadNoSeDeshace() {
        assertEquals(
            TaskFailure.TaskChanged,
            completionUndoFailure(hecha.copy(priority = TaskPriority.HIGH), hecha, me),
        )
    }

    @Test
    fun unCambioDeRepeticionNoSeDeshace() {
        val diaria = TaskRecurrence(RecurrenceUnit.DAY, 1)
        assertEquals(
            TaskFailure.TaskChanged,
            completionUndoFailure(repetidaDespues.copy(recurrence = diaria), repetidaDespues, me),
        )
        assertEquals(
            TaskFailure.TaskChanged,
            completionUndoFailure(repetidaDespues.copy(recurrence = null), repetidaDespues, me),
        )
    }

    @Test
    fun unaFechaMovidaPorLaMismaPersonaNoSeDeshace() {
        assertEquals(
            TaskFailure.TaskChanged,
            completionUndoFailure(hecha.copy(dueAt = hoy), hecha, me),
        )
    }

    @Test
    fun unInicioMovidoNoSeDeshace() {
        assertEquals(
            TaskFailure.TaskChanged,
            completionUndoFailure(repetidaDespues.copy(startAt = ayer), repetidaDespues, me),
        )
        assertEquals(
            TaskFailure.TaskChanged,
            completionUndoFailure(hecha.copy(startAt = ayer), hecha, me),
        )
    }

    @Test
    fun unaRepetidaSeComparaConLasFechasQueDejoAlCompletarla() {
        // Lo que dejó el completar (fechas adelantadas) no es lo de antes: contra lo de antes sí cambió.
        assertNull(completionUndoFailure(repetidaDespues, repetidaDespues, me))
        assertEquals(TaskFailure.TaskChanged, completionUndoFailure(repetidaDespues, repetidaAntes, me))
    }

    @Test
    fun lasFechasSeComparanEnMilisegundos() {
        val conNanos = repetidaDespues.copy(
            startAt = hoy.plusNanos(500),
            dueAt = hoy.plusNanos(500),
        )

        assertNull(completionUndoFailure(repetidaDespues, conNanos, me))
        assertNull(completionUndoFailure(conNanos, repetidaDespues, me))
    }

    @Test
    fun completarUnaNormalLaDejaHechaConSusFechas() {
        val dates = TaskDates(startAt = null, dueAt = ayer)

        val resultado = antes.completedWith(dates)

        assertEquals(TaskStatus.DONE, resultado.status)
        assertNull(resultado.startAt)
        assertEquals(ayer, resultado.dueAt)
    }

    @Test
    fun completarUnaRepetidaLaDejaPendienteConLasFechasNuevas() {
        val dates = TaskDates(startAt = hoy, dueAt = hoy.plusSeconds(60))

        val resultado = repetidaAntes.completedWith(dates)

        assertEquals(TaskStatus.TODO, resultado.status)
        assertEquals(hoy, resultado.startAt)
        assertEquals(hoy.plusSeconds(60), resultado.dueAt)
    }
}
