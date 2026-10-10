package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ExercisePickerFilterTest {
    private fun exercise(id: String, name: String, muscle: String?) = CatalogExercise(
        id = id, name = name, type = ExerciseType.PESO_LIBRE, muscleGroup = muscle, ownerUid = "u",
        createdBy = "u", createdByName = "U", createdAt = Instant.EPOCH, updatedBy = "u", updatedAt = Instant.EPOCH,
    )

    private val catalog = listOf(
        exercise("press-banca", "Press banca", "Pecho"),
        exercise("jalon", "Jalón al pecho", "Espalda"),
        exercise("sentadilla", "Sentadilla", "Cuádriceps"),
    )

    @Test
    fun `empty query returns the whole catalog in order`() {
        assertEquals(catalog, filterExercisePicker(catalog, "  "))
    }

    @Test
    fun `matches folded name and muscle group`() {
        assertEquals(listOf("jalon"), filterExercisePicker(catalog, "JALON").map { it.id })
        assertEquals(listOf("sentadilla"), filterExercisePicker(catalog, "cuadriceps").map { it.id })
        assertEquals(listOf("press-banca", "jalon"), filterExercisePicker(catalog, "pecho").map { it.id })
    }

    @Test
    fun `no match returns empty`() {
        assertEquals(emptyList<CatalogExercise>(), filterExercisePicker(catalog, "zzz"))
    }
}
