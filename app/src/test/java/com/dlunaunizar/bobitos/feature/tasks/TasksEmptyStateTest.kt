package com.dlunaunizar.bobitos.feature.tasks

import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TasksEmptyStateTest {
    @Test fun sinTareasEsLaPrimeraVez() {
        assertEquals(
            TasksEmpty(TasksEmptyKind.FIRST_RUN, canClearFilters = false),
            tasksEmptyState(emptyList(), TaskFilters(), ""),
        )
    }

    @Test fun todoHechoConFiltrosPorDefectoEsAllDone() {
        assertEquals(
            TasksEmpty(TasksEmptyKind.ALL_DONE, canClearFilters = false),
            tasksEmptyState(listOf(tarea(TaskStatus.DONE)), TaskFilters(), ""),
        )
    }

    @Test fun filtrosSinResultadosPermitenQuitarlos() {
        val filters = TaskFilters(priority = TaskPriority.HIGH)
        assertEquals(
            TasksEmpty(TasksEmptyKind.NO_RESULTS, canClearFilters = true),
            tasksEmptyState(listOf(tarea(TaskStatus.TODO)), filters, ""),
        )
    }

    @Test fun sinFiltrosNiBusquedaNoOfreceQuitarFiltros() {
        assertEquals(
            TasksEmpty(TasksEmptyKind.NO_RESULTS, canClearFilters = false),
            tasksEmptyState(listOf(tarea(TaskStatus.TODO)), TaskFilters(), " "),
        )
    }

    private fun tarea(status: TaskStatus) = TaskItem(
        id = "t1",
        title = "Tarea",
        description = null,
        assigneeId = null,
        assigneeName = null,
        dueAt = null,
        priority = TaskPriority.MEDIUM,
        status = status,
        createdBy = "owner",
        createdByName = "David",
        createdAt = Instant.EPOCH,
        updatedBy = "owner",
        updatedAt = Instant.EPOCH,
        completedBy = null,
        completedByName = null,
        completedAt = null,
    )
}
