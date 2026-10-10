package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ExerciseFilterTest {
    private fun exercise(id: String, name: String, muscle: String?, type: ExerciseType = ExerciseType.PESO_LIBRE) =
        CatalogExercise(
            id = id, name = name, type = type, muscleGroup = muscle, ownerUid = "u",
            createdBy = "u", createdByName = "U", createdAt = Instant.EPOCH, updatedBy = "u", updatedAt = Instant.EPOCH,
        )

    private val catalog = listOf(
        exercise("press-banca", "Press banca", "Pecho"),
        exercise("jalon", "Jalón al pecho", "Espalda", ExerciseType.MAQUINA),
        exercise("sentadilla", "Sentadilla", "Cuádriceps"),
        exercise("flexiones", "Flexiones", "pecho", ExerciseType.PESO_CORPORAL),
    )

    private fun ids(filter: ExerciseFilter) = catalog.filterExercises(filter).map { it.id }

    @Test
    fun `empty query returns the whole catalog in order`() {
        assertEquals(catalog, catalog.filterExercises(ExerciseFilter(query = "  ")))
    }

    @Test
    fun `matches folded name and muscle group`() {
        assertEquals(listOf("jalon"), ids(ExerciseFilter(query = "JALON")))
        assertEquals(listOf("sentadilla"), ids(ExerciseFilter(query = "cuadriceps")))
        assertEquals(listOf("press-banca", "jalon", "flexiones"), ids(ExerciseFilter(query = "pecho")))
    }

    @Test
    fun `no match returns empty`() {
        assertEquals(emptyList<String>(), ids(ExerciseFilter(query = "zzz")))
    }

    @Test
    fun `type and group filters combine with the query`() {
        assertEquals(listOf("jalon"), ids(ExerciseFilter(type = ExerciseType.MAQUINA)))
        assertEquals(listOf("flexiones"), ids(ExerciseFilter(query = "pecho", type = ExerciseType.PESO_CORPORAL)))
        assertEquals(listOf("press-banca", "flexiones"), ids(ExerciseFilter(query = "p", muscleGroup = "Pecho")))
        assertEquals(
            emptyList<String>(),
            ids(ExerciseFilter(query = "banca", type = ExerciseType.MAQUINA, muscleGroup = "Pecho")),
        )
    }

    @Test
    fun `group filter ignores accents and case`() {
        assertEquals(listOf("sentadilla"), ids(ExerciseFilter(muscleGroup = "CUADRICEPS")))
        assertEquals(listOf("press-banca", "flexiones"), ids(ExerciseFilter(muscleGroup = "Pecho")))
    }

    @Test
    fun `muscleGroups is unique and sorted`() {
        val groups = (catalog + exercise("x", "X", null) + exercise("y", "Y", "Ángulo")).muscleGroups()
        assertEquals(listOf("Ángulo", "Cuádriceps", "Espalda", "Pecho"), groups)
    }

    @Test
    fun `customExerciseName trims, collapses and caps`() {
        assertEquals("Press banca", customExerciseName("  Press \t  banca\n "))
        assertEquals("", customExerciseName("   "))
        assertEquals(120, customExerciseName("a".repeat(300)).length)
    }
}
