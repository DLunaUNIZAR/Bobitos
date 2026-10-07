package com.dlunaunizar.bobitos.feature.spaces

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLoadPolicyTest {
    @Test
    fun cargaLaPrimeraVez() {
        assertTrue(shouldLoadHome("a", loadedSpaceId = null, hasData = false, force = false))
    }

    @Test
    fun noRecargaSiYaTieneLosDatosDelMismoEspacio() {
        assertFalse(shouldLoadHome("a", loadedSpaceId = "a", hasData = true, force = false))
    }

    @Test
    fun recargaAlCambiarDeEspacio() {
        assertTrue(shouldLoadHome("b", loadedSpaceId = "a", hasData = true, force = false))
    }

    @Test
    fun recargaSiUnaCargaAnteriorFallo() {
        assertTrue(shouldLoadHome("a", loadedSpaceId = "a", hasData = false, force = false))
    }

    @Test
    fun recargaSiSeForzaAunqueTengaLosDatos() {
        assertTrue(shouldLoadHome("a", loadedSpaceId = "a", hasData = true, force = true))
    }
}
