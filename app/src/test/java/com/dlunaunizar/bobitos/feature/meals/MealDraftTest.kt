package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MealDraftTest {
    private fun meal() = Meal(
        id = "m1",
        date = LocalDate.of(2026, 10, 7),
        slot = MealSlot.CENA,
        name = "Lentejas",
        participantIds = listOf("u2", "u1"),
        participantNames = listOf("B", "A"),
        recipeId = "r1",
        cookId = "u1",
        cookName = "A",
        createdBy = "u1",
        createdByName = "A",
        createdAt = Instant.EPOCH,
        updatedBy = "u1",
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun unaComidaNuevaEmpiezaVaciaEnLaFranjaIndicada() {
        val draft = MealDraft.of(null, MealSlot.DESAYUNO)
        assertEquals(MealDraft("", null, emptyList(), null, MealSlot.DESAYUNO), draft)
        assertEquals(MealSlot.DESAYUNO, draft.slot)
    }

    @Test
    fun editarUnaComidaCargaSusDatosYSuFranja() {
        val draft = MealDraft.of(meal(), MealSlot.DESAYUNO)
        assertEquals("Lentejas", draft.name)
        assertEquals("r1", draft.recipeId)
        assertEquals("u1", draft.cookId)
        assertEquals(setOf("u1", "u2"), draft.selectedIds.toSet())
        assertEquals(MealSlot.CENA, draft.slot)
    }

    @Test
    fun quitarAlCocineroDeLosParticipantesLoDescarta() {
        val draft = MealDraft.of(meal(), MealSlot.COMIDA).withParticipant("u1", false)
        assertNull(draft.cookId)
        assertEquals(listOf("u2"), draft.selectedIds)
    }

    @Test
    fun cambiarElNombreADescartaLaRecetaVinculada() {
        val draft = MealDraft.of(meal(), MealSlot.COMIDA).withName("Otra cosa")
        assertNull(draft.recipeId)
    }

    @Test
    fun laFranjaPorDefectoDependeDeLaHora() {
        assertEquals(MealSlot.DESAYUNO, defaultMealSlot(6))
        assertEquals(MealSlot.DESAYUNO, defaultMealSlot(11))
        assertEquals(MealSlot.COMIDA, defaultMealSlot(12))
        assertEquals(MealSlot.COMIDA, defaultMealSlot(16))
        assertEquals(MealSlot.CENA, defaultMealSlot(17))
        assertEquals(MealSlot.CENA, defaultMealSlot(2))
    }
}
