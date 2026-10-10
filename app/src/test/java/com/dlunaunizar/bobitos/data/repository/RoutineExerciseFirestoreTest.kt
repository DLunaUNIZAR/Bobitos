package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.ExerciseSet
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.SetMeasure
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineExerciseFirestoreTest {
    @Test
    fun `PESO_CORPORAL survives serialize and parse`() {
        val original = listOf(
            RoutineExercise(
                name = "Dominadas",
                type = ExerciseType.PESO_CORPORAL,
                sets = listOf(ExerciseSet(reps = 8, weight = 5.0), ExerciseSet(reps = 6, weight = null)),
            ),
        )
        assertEquals(original, parseRoutineExercises(original.toFirestoreExercises()))
    }

    @Test
    fun `unknown type still falls back to OTROS`() {
        val raw = listOf(mapOf("name" to "Yoga", "type" to "FLEXIBILIDAD"))
        assertEquals(ExerciseType.OTROS, parseRoutineExercises(raw)!!.single().type)
    }

    @Test
    fun `SECONDS exercise survives serialize and parse`() {
        val original = listOf(
            RoutineExercise(
                name = "Plancha",
                type = ExerciseType.PESO_CORPORAL,
                sets = listOf(ExerciseSet(seconds = 45), ExerciseSet(seconds = 30, weight = 5.0)),
                measure = SetMeasure.SECONDS,
            ),
        )
        val serialized = original.toFirestoreExercises()
        assertEquals("SECONDS", serialized.single()["measure"])
        assertEquals(original, parseRoutineExercises(serialized))
    }

    @Test
    fun `REPS exercise serializes without measure or seconds keys`() {
        val serialized = listOf(
            RoutineExercise("Press", type = ExerciseType.MAQUINA, sets = listOf(ExerciseSet(reps = 10, weight = 40.0))),
        ).toFirestoreExercises()
        assertEquals(
            listOf(
                mapOf(
                    "name" to "Press",
                    "exerciseId" to null,
                    "type" to "MAQUINA",
                    "sets" to listOf(mapOf("reps" to 10, "weight" to 40.0)),
                    "durationMinutes" to null,
                    "level" to null,
                    "notes" to null,
                ),
            ),
            serialized,
        )
    }

    @Test
    fun `unknown measure falls back to REPS`() {
        val raw = listOf(mapOf("name" to "Plancha", "type" to "PESO_CORPORAL", "measure" to "METROS"))
        assertEquals(SetMeasure.REPS, parseRoutineExercises(raw)!!.single().measure)
        val absent = listOf(mapOf("name" to "Plancha", "type" to "PESO_CORPORAL"))
        assertEquals(SetMeasure.REPS, parseRoutineExercises(absent)!!.single().measure)
    }
}
