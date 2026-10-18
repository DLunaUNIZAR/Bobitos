package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MealSearchTest {
    private val cocido = meal("m1", MealSlot.COMIDA, "Cocido")
    private val tostadas = meal("m2", MealSlot.DESAYUNO, "Tostadas")
    private val dayMeals = listOf(cocido, tostadas)

    @Test
    fun sinBusquedaSalenLasTresFranjas() {
        assertEquals(
            listOf(
                MealSlot.DESAYUNO to listOf(tostadas),
                MealSlot.COMIDA to listOf(cocido),
                MealSlot.CENA to emptyList(),
            ),
            visibleMealSlots(dayMeals, "  "),
        )
    }

    @Test
    fun alBuscarSoloSalenLasFranjasConCoincidencias() {
        assertEquals(listOf(MealSlot.COMIDA to listOf(cocido)), visibleMealSlots(dayMeals, "coci"))
    }

    @Test
    fun alBuscarSinCoincidenciasNoSaleNinguna() {
        assertEquals(emptyList<Pair<MealSlot, List<Meal>>>(), visibleMealSlots(dayMeals, "paella"))
    }

    @Test
    fun conElDiaSinComidasLaBusquedaNoSeAplica() {
        assertEquals(
            MealSlot.entries.map { it to emptyList<Meal>() },
            visibleMealSlots(emptyList(), "paella"),
        )
    }

    private fun meal(id: String, slot: MealSlot, name: String) = Meal(
        id = id,
        date = LocalDate.of(2026, 10, 17),
        slot = slot,
        name = name,
        participantIds = emptyList(),
        participantNames = emptyList(),
        createdBy = "u1",
        createdByName = "Ana",
        createdAt = Instant.parse("2026-10-17T10:00:00Z"),
        updatedBy = "u1",
        updatedAt = Instant.parse("2026-10-17T10:00:00Z"),
    )
}
