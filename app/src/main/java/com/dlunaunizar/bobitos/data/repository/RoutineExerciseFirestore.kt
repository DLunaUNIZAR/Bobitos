package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.ExerciseSet
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.RoutineExercise

// Contrato embebido (acotado) de la lista de ejercicios, compartido por el catálogo de rutinas y por
// la sesión de una actividad de gimnasio: así ambas serializan/parsean exactamente igual y las
// mismas guardas de reglas valen para `routines.exercises` y para `activities.session`.
internal const val MAX_ROUTINE_EXERCISES = 30
internal const val MAX_ROUTINE_SETS = 20

private const val FIELD_NAME = "name"
private const val FIELD_EXERCISE_ID = "exerciseId"
private const val FIELD_TYPE = "type"
private const val FIELD_SETS = "sets"
private const val FIELD_REPS = "reps"
private const val FIELD_WEIGHT = "weight"
private const val FIELD_DURATION = "durationMinutes"
private const val FIELD_LEVEL = "level"
private const val FIELD_NOTES = "notes"

// Serializa la lista (acotada a [MAX_ROUTINE_EXERCISES]/[MAX_ROUTINE_SETS]) a mapas de Firestore.
internal fun List<RoutineExercise>.toFirestoreExercises(): List<Map<String, Any?>> =
    take(MAX_ROUTINE_EXERCISES).map { exercise ->
        mapOf(
            FIELD_NAME to exercise.name,
            FIELD_EXERCISE_ID to exercise.exerciseId,
            FIELD_TYPE to exercise.type.name,
            FIELD_SETS to exercise.sets.take(MAX_ROUTINE_SETS)
                .map { mapOf(FIELD_REPS to it.reps, FIELD_WEIGHT to it.weight) },
            FIELD_DURATION to exercise.durationMinutes,
            FIELD_LEVEL to exercise.level,
            FIELD_NOTES to exercise.notes,
        )
    }

// Retro-compat: valor ausente/no-lista → null; presente → lista parseada por elemento (los
// malformados se descartan; tipo desconocido cae a OTROS).
internal fun parseRoutineExercises(raw: Any?): List<RoutineExercise>? {
    val list = raw as? List<*> ?: return null
    return list.mapNotNull { element ->
        val map = element as? Map<*, *> ?: return@mapNotNull null
        val name = (map[FIELD_NAME] as? String)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val type = (map[FIELD_TYPE] as? String)?.let { runCatching { ExerciseType.valueOf(it) }.getOrNull() }
            ?: ExerciseType.OTROS
        RoutineExercise(
            name = name,
            exerciseId = (map[FIELD_EXERCISE_ID] as? String)?.takeIf(String::isNotBlank),
            type = type,
            sets = (map[FIELD_SETS] as? List<*>).orEmpty().mapNotNull { setElement ->
                val setMap = setElement as? Map<*, *> ?: return@mapNotNull null
                ExerciseSet(
                    reps = (setMap[FIELD_REPS] as? Number)?.toInt(),
                    weight = (setMap[FIELD_WEIGHT] as? Number)?.toDouble(),
                )
            },
            durationMinutes = (map[FIELD_DURATION] as? Number)?.toInt(),
            level = (map[FIELD_LEVEL] as? String)?.takeIf(String::isNotBlank),
            notes = (map[FIELD_NOTES] as? String)?.takeIf(String::isNotBlank),
        )
    }
}
