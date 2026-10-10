package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseSet
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.SetMeasure
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

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

    @Test
    fun `drafts round-trip measure and seconds`() {
        val original = listOf(
            RoutineExercise(
                name = "Plancha",
                type = ExerciseType.PESO_CORPORAL,
                sets = listOf(ExerciseSet(seconds = 45), ExerciseSet(seconds = 30, weight = 2.5)),
                measure = SetMeasure.SECONDS,
            ),
        )
        assertEquals(original, original.toExerciseDrafts().toRoutineExercises())
    }

    @Test
    fun `timed sets keep seconds and optional weight`() {
        val draft = ExerciseDraft(
            name = "Plancha",
            type = ExerciseType.PESO_CORPORAL,
            measure = SetMeasure.SECONDS,
            sets = listOf(SetDraft(seconds = "40", weight = "5,5"), SetDraft(reps = "9", seconds = "")),
        )
        val sets = listOf(draft).toRoutineExercises().single().sets
        assertEquals(listOf(ExerciseSet(seconds = 40, weight = 5.5), ExerciseSet()), sets)
    }

    @Test
    fun `switching a timed exercise to cardio drops sets and measure`() {
        val draft = ExerciseDraft(
            name = "Plancha",
            type = ExerciseType.PESO_CORPORAL,
            measure = SetMeasure.SECONDS,
            sets = listOf(SetDraft(seconds = "40")),
        )
        draft.type = ExerciseType.CARDIO
        val exercise = listOf(draft).toRoutineExercises().single()
        assertEquals(emptyList<ExerciseSet>(), exercise.sets)
        assertEquals(SetMeasure.REPS, exercise.measure)
    }

    @Test
    fun `a timed catalog exercise starts in seconds`() {
        val now = Instant.EPOCH
        val catalog = CatalogExercise(
            id = "plancha",
            name = "Plancha",
            type = ExerciseType.PESO_CORPORAL,
            measure = SetMeasure.SECONDS,
            ownerUid = "u",
            createdBy = "u",
            createdByName = "U",
            createdAt = now,
            updatedBy = "u",
            updatedAt = now,
        )
        val draft = catalog.toExerciseDraft()
        assertEquals(SetMeasure.SECONDS, draft.measure)
        assertEquals("plancha", draft.exerciseId)
        assertEquals(ExerciseType.PESO_CORPORAL, draft.type)
    }
}
