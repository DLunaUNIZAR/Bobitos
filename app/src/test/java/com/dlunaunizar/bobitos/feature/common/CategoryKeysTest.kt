package com.dlunaunizar.bobitos.feature.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryKeysTest {
    @Test
    fun lasOpcionesJuntanMayusculasYEspaciosYOrdenanConAcentos() {
        val options = listOf("Postres", " postres ", "Ácidos", "Bebidas", "").categoryOptions { it }

        assertEquals(listOf("Ácidos", "Bebidas", "Postres"), options)
    }

    @Test
    fun laMismaCategoriaIgnoraMayusculasYEspacios() {
        assertTrue(" Postres ".sameCategoryAs("postres"))
        assertFalse("Postres".sameCategoryAs("Bebidas"))
        assertTrue(null.sameCategoryAs(null))
        assertFalse(null.sameCategoryAs("Postres"))
        assertFalse("Postres".sameCategoryAs(null))
    }
}
