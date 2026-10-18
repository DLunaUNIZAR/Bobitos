package com.dlunaunizar.bobitos.core.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayFocusTest {
    private val focus = TodayFocus()

    @Test
    fun elModuloPedidoDesdeHoyLlegaEnfocadoEnHoyUnaSolaVez() {
        focus.request(BobitosDestination.Meals)

        assertTrue(focus.take(BobitosDestination.Meals))
        // Después, al volver a él por su pestaña, se restaura como siempre.
        assertFalse(focus.take(BobitosDestination.Meals))
    }

    @Test
    fun otroModuloNoGastaLaPeticion() {
        focus.request(BobitosDestination.Calendar)

        assertFalse(focus.take(BobitosDestination.Meals))
        assertTrue(focus.take(BobitosDestination.Calendar))
    }

    @Test
    fun sinPeticionNadaSeEnfoca() {
        assertFalse(focus.take(BobitosDestination.Sport))
    }

    @Test
    fun laUltimaPeticionSustituyeAlaAnterior() {
        focus.request(BobitosDestination.Meals)
        focus.request(BobitosDestination.Tasks)

        assertFalse(focus.take(BobitosDestination.Meals))
    }
}
