package com.dlunaunizar.bobitos.feature.exercises

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseType
import kotlinx.parcelize.Parcelize

// Borrador del editor de ejercicio del catálogo. Parcelable para que sobreviva a una rotación.
@Parcelize
internal data class CatalogExerciseDraft(val name: String, val type: ExerciseType, val muscle: String) : Parcelable {
    companion object {
        fun of(exercise: CatalogExercise?) = CatalogExerciseDraft(
            name = exercise?.name.orEmpty(),
            type = exercise?.type ?: ExerciseType.MAQUINA,
            muscle = exercise?.muscleGroup.orEmpty(),
        )
    }
}
