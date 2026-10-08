package com.dlunaunizar.bobitos.feature.notes

import com.dlunaunizar.bobitos.core.model.Note
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class NoteDraftTest {
    private fun note(body: String?) = Note(
        id = "n1", title = "Compra", body = body, pinned = false, createdBy = "u", createdByName = "U",
        createdAt = Instant.EPOCH, updatedBy = "u", updatedAt = Instant.EPOCH,
    )

    @Test
    fun unaNotaNuevaEmpiezaVacia() {
        assertEquals(NoteDraft("", ""), NoteDraft.of(null))
    }

    @Test
    fun editarCargaTituloYCuerpoYUnCuerpoNuloEsVacio() {
        assertEquals(NoteDraft("Compra", "leche"), NoteDraft.of(note("leche")))
        assertEquals(NoteDraft("Compra", ""), NoteDraft.of(note(null)))
    }
}
