package com.dlunaunizar.bobitos.feature.spaces

import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TodayGreetingTest {
    @Test
    fun elSaludoDependeDeLaHora() {
        assertEquals(DayPeriod.MORNING, dayPeriodFor(5))
        assertEquals(DayPeriod.MORNING, dayPeriodFor(11))
        assertEquals(DayPeriod.AFTERNOON, dayPeriodFor(12))
        assertEquals(DayPeriod.AFTERNOON, dayPeriodFor(19))
        assertEquals(DayPeriod.EVENING, dayPeriodFor(20))
        assertEquals(DayPeriod.EVENING, dayPeriodFor(3))
    }

    @Test
    fun elNombreEsLaPrimeraPalabra() {
        assertEquals("David", firstName("David Luna"))
        assertEquals("Ana", firstName("  Ana  "))
        assertEquals("", firstName("   "))
    }

    @Test
    fun cuentaLosElementosDeHoy() {
        val empty = SpaceHomeDigest(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(0, empty.todayItemCount())
    }

    @Test
    fun sumaLasComidasQueCocinaYLasQueCome() {
        val digest = SpaceHomeDigest(
            myTasksToday = emptyList(),
            myCookingToday = listOf(meal("a")),
            myEatingToday = listOf(meal("b"), meal("c")),
            myEventsToday = emptyList(),
            workload = emptyList(),
        )
        assertEquals(3, digest.todayItemCount())
    }

    private fun meal(id: String) = Meal(
        id = id,
        date = LocalDate.of(2026, 7, 25),
        slot = MealSlot.COMIDA,
        name = id,
        participantIds = emptyList(),
        participantNames = emptyList(),
        createdBy = "owner",
        createdByName = "Owner",
        createdAt = Instant.EPOCH,
        updatedBy = "owner",
        updatedAt = Instant.EPOCH,
    )
}
