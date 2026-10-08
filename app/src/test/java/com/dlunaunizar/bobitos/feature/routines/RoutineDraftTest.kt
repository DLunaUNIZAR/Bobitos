package com.dlunaunizar.bobitos.feature.routines

import androidx.compose.runtime.saveable.SaverScope
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RoutineDraftTest {
    private val allSaveable = SaverScope { true }

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

    @Test
    fun elBorradorSobreviveAUnaRotacion() {
        val draft = RoutineDraft("Pierna", "lunes", true)
        val restored = with(RoutineDraftSaver) { restore(allSaveable.save(draft)!!) }
        assertEquals(draft, restored)
    }
}
