package com.dlunaunizar.bobitos.feature.routines

import androidx.compose.runtime.saveable.listSaver
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineVisibility

// Campos simples del editor de rutina, para que sobrevivan a una rotación. La lista de ejercicios se
// guarda aparte (ExerciseDraftListSaver).
internal data class RoutineDraft(val title: String, val description: String, val global: Boolean) {
    companion object {
        fun of(routine: Routine?) = RoutineDraft(
            title = routine?.title.orEmpty(),
            description = routine?.description.orEmpty(),
            global = routine?.visibility == RoutineVisibility.GLOBAL,
        )
    }
}

internal val RoutineDraftSaver = listSaver<RoutineDraft, Any?>(
    save = { listOf(it.title, it.description, it.global) },
    restore = {
        RoutineDraft(title = it[0] as String, description = it[1] as String, global = it[2] as Boolean)
    },
)
