package com.dlunaunizar.bobitos.feature.routines

import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import com.dlunaunizar.bobitos.core.model.SetMeasure

// «Mis rutinas» enseña solo las privadas del usuario: las GLOBAL (también las del catálogo, cuyo dueño es
// el admin) van siempre a «Comunes».
internal fun List<Routine>.personalOnly(): List<Routine> = filter { it.visibility == RoutineVisibility.PRIVATE }

// «Guardar como mía» sale en cualquier común, también para el admin, si se puede escribir.
internal fun canForkRoutine(routine: Routine, canWrite: Boolean): Boolean =
    canWrite && routine.visibility == RoutineVisibility.GLOBAL

// Pauta común de las series de fuerza: número de series y repeticiones (o segundos, si se miden por
// tiempo), solo si todas las series tienen el mismo valor. Null si no hay series o difieren.
internal data class SetsPattern(val count: Int, val value: Int, val timed: Boolean)

internal fun RoutineExercise.setsPattern(): SetsPattern? {
    val timed = measure == SetMeasure.SECONDS
    val values = sets.map { if (timed) it.seconds else it.reps }
    val first = values.firstOrNull() ?: return null
    return if (values.all { it == first }) SetsPattern(values.size, first, timed) else null
}
