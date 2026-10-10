package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.ExerciseSet
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.RoutineExercise
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
}
