package com.dlunaunizar.bobitos.feature.routines

import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineVisibility

// «Mis rutinas» enseña solo las privadas del usuario: las GLOBAL (también las del catálogo, cuyo dueño es
// el admin) van siempre a «Comunes».
internal fun List<Routine>.personalOnly(): List<Routine> = filter { it.visibility == RoutineVisibility.PRIVATE }

// «Guardar como mía» sale en cualquier común, también para el admin, si se puede escribir.
internal fun canForkRoutine(routine: Routine, canWrite: Boolean): Boolean =
    canWrite && routine.visibility == RoutineVisibility.GLOBAL
