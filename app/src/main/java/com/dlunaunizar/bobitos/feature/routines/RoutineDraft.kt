package com.dlunaunizar.bobitos.feature.routines

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import kotlinx.parcelize.Parcelize

// Campos simples del editor de rutina, Parcelable para que sobrevivan a una rotación. La lista de
// ejercicios se guarda aparte (ExerciseDraftListSaver).
@Parcelize
internal data class RoutineDraft(val title: String, val description: String, val global: Boolean) : Parcelable {
    companion object {
        fun of(routine: Routine?) = RoutineDraft(
            title = routine?.title.orEmpty(),
            description = routine?.description.orEmpty(),
            global = routine?.visibility == RoutineVisibility.GLOBAL,
        )
    }
}
