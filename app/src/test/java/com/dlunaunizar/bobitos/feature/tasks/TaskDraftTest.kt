package com.dlunaunizar.bobitos.feature.tasks

import androidx.compose.runtime.saveable.SaverScope
import com.dlunaunizar.bobitos.core.model.RecurrenceUnit
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskRecurrence
import com.dlunaunizar.bobitos.core.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskDraftTest {
    private val allSaveable = SaverScope { true }

    private fun roundTrip(draft: TaskDraft): TaskDraft? = with(TaskDraftSaver) {
        restore(allSaveable.save(draft)!!)
    }

    @Test
    fun elBorradorCompletoSobreviveAUnaRotacion() {
        val draft = TaskDraft(
            title = "Fregar",
            description = "con lejía",
            assigneeId = "u1",
            startDate = "2026-10-01",
            dueDate = "2026-10-08",
            priorityName = TaskPriority.HIGH.name,
            typeName = TaskType.LIMPIEZA.name,
            recurrenceUnit = RecurrenceUnit.WEEK.name,
            recurrenceInterval = 2,
        )
        assertEquals(draft, roundTrip(draft))
    }

    @Test
    fun elBorradorConCamposVaciosSobreviveAUnaRotacion() {
        val draft = TaskDraft("", "", null, "", "", TaskPriority.MEDIUM.name, null, null, 1)
        assertEquals(draft, roundTrip(draft))
    }

    @Test
    fun lasPropiedadesDerivadasReconstruyenLosEnums() {
        val draft = TaskDraft(
            "t", "", null, "", "", TaskPriority.LOW.name, TaskType.LIMPIEZA.name, RecurrenceUnit.DAY.name, 3,
        )
        assertEquals(TaskPriority.LOW, draft.priority)
        assertEquals(TaskType.LIMPIEZA, draft.type)
        assertEquals(TaskRecurrence(RecurrenceUnit.DAY, 3), draft.recurrence)
    }

    @Test
    fun sinTipoNiRecurrenciaLasPropiedadesSonNulas() {
        val draft = TaskDraft("t", "", null, "", "", TaskPriority.MEDIUM.name, null, null, 1)
        assertNull(draft.type)
        assertNull(draft.recurrence)
    }
}
