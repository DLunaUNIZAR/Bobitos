package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class CatalogExerciseDraftTest {
    private fun exercise(muscle: String?) = CatalogExercise(
        id = "e1", name = "Press banca", type = ExerciseType.PESO_LIBRE, muscleGroup = muscle, ownerUid = "u",
        createdBy = "u", createdByName = "U", createdAt = Instant.EPOCH, updatedBy = "u", updatedAt = Instant.EPOCH,
    )

    @Test
    fun unEjercicioNuevoEmpiezaVacioYDeMaquina() {
        val draft = CatalogExerciseDraft.of(null)
        assertEquals(CatalogExerciseDraft("", ExerciseType.MAQUINA, ""), draft)
        assertEquals(ExerciseType.MAQUINA, draft.type)
    }

    @Test
    fun editarCargaSusDatosYUnMusculoNuloEsVacio() {
        assertEquals(
            CatalogExerciseDraft("Press banca", ExerciseType.PESO_LIBRE, "pecho"),
            CatalogExerciseDraft.of(exercise("pecho")),
        )
        assertEquals("", CatalogExerciseDraft.of(exercise(null)).muscle)
    }
}
