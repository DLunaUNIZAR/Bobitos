package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseEquipment
import com.dlunaunizar.bobitos.core.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ExerciseThumbnailTest {
    @Test
    fun unaImagenGrandeSeSubmuestrea() {
        // 768 / 4 = 192 >= 120, pero 768 / 8 = 96 < 120.
        assertEquals(4, sampleSizeFor(width = 1024, height = 768, targetPx = 120))
    }

    @Test
    fun unaImagenPequenaNoSeSubmuestrea() {
        assertEquals(1, sampleSizeFor(width = 100, height = 80, targetPx = 120))
    }

    @Test
    fun laLineaSecundariaLlevaTipoYMaterial() {
        val exercise = exercise(ExerciseType.PESO_LIBRE, listOf(ExerciseEquipment.MANCUERNAS, ExerciseEquipment.BANCO))

        assertEquals(
            listOf(
                R.string.exercise_type_free_weight,
                R.string.exercise_equipment_dumbbells,
                R.string.exercise_equipment_bench,
            ),
            exerciseSupportingRes(exercise),
        )
    }

    private fun exercise(type: ExerciseType, equipment: List<ExerciseEquipment>) = CatalogExercise(
        id = "e1",
        name = "Press",
        type = type,
        equipment = equipment,
        ownerUid = "u1",
        createdBy = "u1",
        createdByName = "Ana",
        createdAt = Instant.parse("2026-10-17T10:00:00Z"),
        updatedBy = "u1",
        updatedAt = Instant.parse("2026-10-17T10:00:00Z"),
    )
}
