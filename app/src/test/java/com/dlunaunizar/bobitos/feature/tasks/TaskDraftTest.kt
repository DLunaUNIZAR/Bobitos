package com.dlunaunizar.bobitos.feature.tasks

import com.dlunaunizar.bobitos.core.model.RecurrenceUnit
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskRecurrence
import com.dlunaunizar.bobitos.core.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskDraftTest {
    @Test
    fun laRecurrenciaSeComponeDeUnidadEIntervalo() {
        val draft = TaskDraft("t", "", null, "", "", TaskPriority.LOW, TaskType.LIMPIEZA, RecurrenceUnit.DAY, 3)
        assertEquals(TaskRecurrence(RecurrenceUnit.DAY, 3), draft.recurrence)
    }

    @Test
    fun sinUnidadNoHayRecurrencia() {
        val draft = TaskDraft("t", "", null, "", "", TaskPriority.MEDIUM, null, null, 1)
        assertNull(draft.recurrence)
    }
}
