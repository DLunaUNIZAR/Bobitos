package com.dlunaunizar.bobitos.feature.exercises

import androidx.compose.runtime.saveable.listSaver
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseType

// Borrador del editor de ejercicio del catálogo. `typeName` es el `name` del enum (cabe en un Bundle).
internal data class CatalogExerciseDraft(val name: String, val typeName: String, val muscle: String) {
    val type: ExerciseType get() = ExerciseType.valueOf(typeName)

    companion object {
        fun of(exercise: CatalogExercise?) = CatalogExerciseDraft(
            name = exercise?.name.orEmpty(),
            typeName = (exercise?.type ?: ExerciseType.MAQUINA).name,
            muscle = exercise?.muscleGroup.orEmpty(),
        )
    }
}

internal val CatalogExerciseDraftSaver = listSaver<CatalogExerciseDraft, Any?>(
    save = { listOf(it.name, it.typeName, it.muscle) },
    restore = {
        CatalogExerciseDraft(name = it[0] as String, typeName = it[1] as String, muscle = it[2] as String)
    },
)
