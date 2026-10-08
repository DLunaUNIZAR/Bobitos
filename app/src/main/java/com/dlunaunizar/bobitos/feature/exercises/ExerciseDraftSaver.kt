package com.dlunaunizar.bobitos.feature.exercises

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.dlunaunizar.bobitos.core.model.ExerciseType

// Guardado de la lista de ejercicios del editor (rutina o sesión de gimnasio) para que sobreviva a una
// rotación. Cada ejercicio se guarda como una lista de textos y números; las series, anidadas.
// Orden: nombre, exerciseId, tipo (name), series [(reps, peso)…], duración, nivel, notas.
// Se antepone una cabecera para que la lista guardada nunca esté vacía: `listSaver` devuelve null para
// una lista vacía y `rememberSaveable` lo interpreta como «sin valor», con lo que borrar todos los
// ejercicios y girar los devolvería a su estado inicial.
internal val ExerciseDraftListSaver: Saver<SnapshotStateList<ExerciseDraft>, Any> =
    listSaver<SnapshotStateList<ExerciseDraft>, Any>(
        save = { drafts -> listOf<Any>(SAVED_LIST_HEADER) + drafts.map { it.toSaveable() } },
        restore = { saved ->
            saved.drop(1).map { item ->
                @Suppress("UNCHECKED_CAST")
                (item as List<Any?>).toExerciseDraft()
            }.toMutableStateList()
        },
    )

private const val SAVED_LIST_HEADER = 1

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
