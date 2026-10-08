package com.dlunaunizar.bobitos.feature.routines

import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RoutineDraftTest {
    private fun routine(visibility: RoutineVisibility, description: String?) = Routine(
        id = "r1", ownerUid = "u", visibility = visibility, title = "Pierna", description = description,
        createdBy = "u", createdByName = "U", createdAt = Instant.EPOCH, updatedBy = "u", updatedAt = Instant.EPOCH,
    )

    @Test
    fun unaRutinaNuevaEmpiezaVaciaYPrivada() {
        assertEquals(RoutineDraft("", "", false), RoutineDraft.of(null))
    }

    @Test
    fun editarCargaSusDatosYUnaDescripcionNulaEsVacia() {
        assertEquals(
            RoutineDraft("Pierna", "lunes", true),
            RoutineDraft.of(routine(RoutineVisibility.GLOBAL, "lunes")),
        )
        assertEquals(
            RoutineDraft("Pierna", "", false),
            RoutineDraft.of(routine(RoutineVisibility.PRIVATE, null)),
        )
    }
}
