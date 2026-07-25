@file:Suppress("MatchingDeclarationName")

package com.dlunaunizar.bobitos.feature.tasks

import androidx.annotation.StringRes
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.RecurrenceUnit
import com.dlunaunizar.bobitos.core.model.TaskRecurrence
import com.dlunaunizar.bobitos.core.model.TaskType

// Plantilla estática de una faena del hogar: prerrellena el editor (título + tipo + recurrencia).
// No es un documento en Firestore: es una lista fija en la app (cero datos/reglas/lecturas).
data class TaskTemplate(@param:StringRes val titleRes: Int, val type: TaskType?, val recurrence: TaskRecurrence?)

// Faenas típicas del hogar para acelerar el arranque y fomentar recurrencia/tipos.
val homeTaskTemplates: List<TaskTemplate> = listOf(
    TaskTemplate(R.string.tasks_tpl_bathroom, TaskType.LIMPIEZA, TaskRecurrence(RecurrenceUnit.WEEK, 1)),
    TaskTemplate(R.string.tasks_tpl_kitchen, TaskType.LIMPIEZA, TaskRecurrence(RecurrenceUnit.WEEK, 1)),
    TaskTemplate(R.string.tasks_tpl_vacuum, TaskType.LIMPIEZA, TaskRecurrence(RecurrenceUnit.WEEK, 1)),
    TaskTemplate(R.string.tasks_tpl_trash, TaskType.HOGAR, TaskRecurrence(RecurrenceUnit.DAY, 1)),
    TaskTemplate(R.string.tasks_tpl_dishes, TaskType.HOGAR, TaskRecurrence(RecurrenceUnit.DAY, 1)),
    TaskTemplate(R.string.tasks_tpl_laundry, TaskType.HOGAR, TaskRecurrence(RecurrenceUnit.WEEK, 1)),
    TaskTemplate(R.string.tasks_tpl_sheets, TaskType.HOGAR, TaskRecurrence(RecurrenceUnit.WEEK, 2)),
    TaskTemplate(R.string.tasks_tpl_groceries, TaskType.COMPRAS, TaskRecurrence(RecurrenceUnit.WEEK, 1)),
)
