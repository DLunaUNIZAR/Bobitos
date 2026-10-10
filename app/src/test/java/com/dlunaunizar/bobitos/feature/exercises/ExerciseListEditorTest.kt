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
    fun `bodyweight sets keep reps and optional ballast`() {
        val drafts = listOf(
            ExerciseDraft(
                name = "Dominadas",
                type = ExerciseType.PESO_CORPORAL,
                sets = listOf(SetDraft("12", ""), SetDraft("8", "5,5")),
            ),
        )

        assertEquals(
            listOf(ExerciseSet(12, null), ExerciseSet(8, 5.5)),
            drafts.toRoutineExercises().single().sets,
        )
        assertEquals(null, drafts.toRoutineExercises().single().durationMinutes)
        assertEquals(null, drafts.toRoutineExercises().single().level)
    }

    @Test
    fun `blank notes normalize to null`() {
        val drafts = listOf(RoutineExercise("Sentadilla", type = ExerciseType.MAQUINA)).toExerciseDrafts()
        drafts.first().notes = "   "

        assertEquals(null, drafts.toRoutineExercises().first().notes)
    }
}
