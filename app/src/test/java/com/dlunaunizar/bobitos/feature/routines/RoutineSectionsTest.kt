package com.dlunaunizar.bobitos.feature.routines

import com.dlunaunizar.bobitos.core.model.ExerciseSet
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import com.dlunaunizar.bobitos.core.model.SetMeasure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RoutineSectionsTest {
    @Test
    fun `my routines only lists private ones, also for the catalog admin`() {
        val mine = listOf(
            routine("p1", RoutineVisibility.PRIVATE),
            routine("torso", RoutineVisibility.GLOBAL),
            routine("p2", RoutineVisibility.PRIVATE),
        )
        assertEquals(listOf("p1", "p2"), mine.personalOnly().map(Routine::id))
    }

    @Test
    fun `any common routine can be saved as mine when writing is allowed`() {
        assertTrue(canForkRoutine(routine("torso", RoutineVisibility.GLOBAL), canWrite = true))
        assertFalse(canForkRoutine(routine("torso", RoutineVisibility.GLOBAL), canWrite = false))
        assertFalse(canForkRoutine(routine("p1", RoutineVisibility.PRIVATE), canWrite = true))
    }

    @Test
    fun `sets pattern is shared reps or seconds when all sets agree`() {
        val reps = strength(List(3) { ExerciseSet(reps = 12) })
        assertEquals(SetsPattern(3, 12, false), reps.setsPattern())
        val timed = strength(List(3) { ExerciseSet(seconds = 30) }, SetMeasure.SECONDS)
        assertEquals(SetsPattern(3, 30, true), timed.setsPattern())
    }

    @Test
    fun `sets pattern is null when sets differ, lack the value or do not exist`() {
        assertNull(strength(listOf(ExerciseSet(reps = 12), ExerciseSet(reps = 10))).setsPattern())
        assertNull(strength(listOf(ExerciseSet(reps = 12), ExerciseSet())).setsPattern())
        assertNull(strength(emptyList()).setsPattern())
    }

    private fun strength(sets: List<ExerciseSet>, measure: SetMeasure = SetMeasure.REPS) = RoutineExercise(
        name = "Sentadilla",
        type = ExerciseType.PESO_CORPORAL,
        sets = sets,
        measure = measure,
    )

    private fun routine(id: String, visibility: RoutineVisibility) = Routine(
        id = id,
        ownerUid = "admin",
        visibility = visibility,
        title = id,
        createdBy = "admin",
        createdByName = "Catálogo Bobitos",
        createdAt = Instant.EPOCH,
        updatedBy = "admin",
        updatedAt = Instant.EPOCH,
    )
}
