package com.dlunaunizar.bobitos.feature.routines

import androidx.annotation.StringRes
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import com.dlunaunizar.bobitos.core.model.SetMeasure

// «Mis rutinas» (y el selector de la sesión) toman solo las privadas del usuario: las GLOBAL (también las
// del catálogo, cuyo dueño es el admin) llegan siempre por la lista de comunes.
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

// Etiqueta del origen de una rutina en el selector de la sesión: las privadas son del usuario; las GLOBAL,
// comunes (también las que publica el admin).
@StringRes
internal fun routineOriginRes(routine: Routine): Int = when (routine.visibility) {
    RoutineVisibility.PRIVATE -> R.string.sport_routine_origin_mine
    else -> R.string.sport_routine_origin_common
}
