package com.dlunaunizar.bobitos.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DestinationIconsTest {
    @Test
    fun deporteYEjerciciosNoCompartenIcono() {
        assertNotEquals(BobitosDestination.Sport.icon, BobitosDestination.Exercises.icon)
    }

    @Test
    fun enMasYEnElInicioNingunaFilaRepiteIcono() {
        val filas = BobitosDestination.moreDestinations +
            BobitosDestination.catalogDestinations +
            BobitosDestination.MyCalendar
        val iconos = filas.map { it.icon }
        assertEquals("Hay iconos repetidos en $filas", iconos.size, iconos.toSet().size)
    }
}
