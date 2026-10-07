package com.dlunaunizar.bobitos.feature.notes

import androidx.compose.runtime.saveable.SaverScope
import com.dlunaunizar.bobitos.core.model.Note
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class NoteDraftTest {
    private val allSaveable = SaverScope { true }

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

    @Test
    fun elBorradorSobreviveAUnaRotacion() {
        val draft = NoteDraft("Compra", "leche\npan")
        val restored = with(NoteDraftSaver) { restore(allSaveable.save(draft)!!) }
        assertEquals(draft, restored)
    }
}
