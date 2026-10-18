package com.dlunaunizar.bobitos.feature.tasks

import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskStatus

enum class TasksEmptyKind { FIRST_RUN, ALL_DONE, NO_RESULTS }

/** Qué estado vacío pintar y si tiene sentido ofrecer «Quitar filtros». */
data class TasksEmpty(val kind: TasksEmptyKind, val canClearFilters: Boolean)

/** Decide el estado vacío de Tareas. Se llama solo cuando la lista visible está vacía. */
fun tasksEmptyState(allTasks: List<TaskItem>, filters: TaskFilters, query: String): TasksEmpty {
    val untouched = filters == TaskFilters() && query.isBlank()
    return when {
        allTasks.isEmpty() -> TasksEmpty(TasksEmptyKind.FIRST_RUN, canClearFilters = false)
        untouched && allTasks.none { it.status == TaskStatus.TODO } ->
            TasksEmpty(TasksEmptyKind.ALL_DONE, canClearFilters = false)
        else -> TasksEmpty(TasksEmptyKind.NO_RESULTS, canClearFilters = !untouched)
    }
}
