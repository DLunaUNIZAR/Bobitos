package com.dlunaunizar.bobitos.feature.sport

import com.dlunaunizar.bobitos.core.model.SportActivity
import com.dlunaunizar.bobitos.core.model.SportType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ActivityDraftTest {
    private fun activity() = SportActivity(
        id = "a1",
        date = LocalDate.of(2026, 10, 8),
        type = SportType.GIMNASIO,
        name = "Pierna",
        participantIds = listOf("u2", "u1"),
        participantNames = listOf("B", "A"),
        routineId = "r1",
        createdBy = "u1",
        createdByName = "A",
        createdAt = Instant.EPOCH,
        updatedBy = "u1",
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun unaActividadNuevaEmpiezaDePadelSinNombreNiParticipantes() {
        val draft = ActivityDraft.of(null)
        assertEquals(ActivityDraft(SportType.PADEL, "", emptyList(), null), draft)
        assertEquals(SportType.PADEL, draft.type)
    }

    @Test
    fun editarCargaSusDatosConLosParticipantesOrdenados() {
        val draft = ActivityDraft.of(activity())
        assertEquals(SportType.GIMNASIO, draft.type)
        assertEquals("Pierna", draft.name)
        assertEquals(listOf("u1", "u2"), draft.selectedIds)
        assertEquals("r1", draft.routineId)
    }

    @Test
    fun marcarYDesmarcarUnParticipanteVuelveAlBorradorInicial() {
        val initial = ActivityDraft.of(activity())
        val toggled = initial.withParticipant("u1", false).withParticipant("u1", true)
        assertEquals(initial, toggled)
    }
}
