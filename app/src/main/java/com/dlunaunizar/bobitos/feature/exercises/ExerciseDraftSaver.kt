package com.dlunaunizar.bobitos.feature.exercises

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.dlunaunizar.bobitos.core.model.ExerciseType

// Guardado de la lista de ejercicios del editor (rutina o sesión de gimnasio) para que sobreviva a una
// rotación. Cada ejercicio se guarda como una lista de textos y números; las series, anidadas.
// Orden: nombre, exerciseId, tipo (name), series [(reps, peso)…], duración, nivel, notas.
// Es un Saver normal y no un listSaver: listSaver guarda null para una lista vacía y rememberSaveable lo
// interpretaría como «sin valor», con lo que borrar todos los ejercicios y girar los devolvería.
internal val ExerciseDraftListSaver: Saver<SnapshotStateList<ExerciseDraft>, Any> = Saver(
    save = { drafts -> ArrayList(drafts.map { it.toSaveable() }) },
    restore = { saved ->
        @Suppress("UNCHECKED_CAST")
        (saved as List<List<Any?>>).map { it.toExerciseDraft() }.toMutableStateList()
    },
)

private fun ExerciseDraft.toSaveable(): ArrayList<Any?> = arrayListOf(
    name,
    exerciseId,
    type.name,
    ArrayList(sets.map { arrayListOf(it.reps, it.weight) }),
    duration,
    level,
    notes,
)

private fun List<Any?>.toExerciseDraft(): ExerciseDraft {
    @Suppress("UNCHECKED_CAST")
    val sets = (this[3] as List<List<String>>).map { SetDraft(reps = it[0], weight = it[1]) }
    return ExerciseDraft(
        name = this[0] as String,
        exerciseId = this[1] as String?,
        type = ExerciseType.valueOf(this[2] as String),
        sets = sets,
        duration = this[4] as String,
        level = this[5] as String,
        notes = this[6] as String,
    )
}
