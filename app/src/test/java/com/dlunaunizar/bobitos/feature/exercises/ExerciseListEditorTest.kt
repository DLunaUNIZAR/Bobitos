package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.core.model.ExerciseSet
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseListEditorTest {
    @Test
    fun `round-trips a strength exercise including notes`() {
        val original = listOf(
            RoutineExercise(
                name = "Press banca",
                type = ExerciseType.PESO_LIBRE,
                sets = listOf(ExerciseSet(reps = 10, weight = 62.5)),
                notes = "Bajar controlado",
            ),
        )

        assertEquals(original, original.toExerciseDrafts().toRoutineExercises())
    }

    @Test
    fun `round-trips a cardio exercise including notes`() {
        val original = listOf(
            RoutineExercise(
                name = "Cinta",
                type = ExerciseType.CARDIO,
                durationMinutes = 20,
                level = "7",
                notes = "Inclinación 5%",
            ),
        )

        assertEquals(original, original.toExerciseDrafts().toRoutineExercises())
    }

    @Test
    fun `blank notes normalize to null`() {
        val drafts = listOf(RoutineExercise("Sentadilla", type = ExerciseType.MAQUINA)).toExerciseDrafts()
        drafts.first().notes = "   "

        assertEquals(null, drafts.toRoutineExercises().first().notes)
    }
}
