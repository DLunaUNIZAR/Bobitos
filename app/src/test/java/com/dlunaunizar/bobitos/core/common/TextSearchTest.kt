package com.dlunaunizar.bobitos.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {
    @Test
    fun foldQuitaTildesYMayusculas() {
        assertEquals("jalon", "Jalón".foldForSearch())
        assertEquals("ano", "Año".foldForSearch())
    }

    @Test
    fun jalonEncuentraJalonConTilde() {
        assertTrue(matchesQuery("jalon", "Jalón al pecho"))
        assertTrue(matchesQuery("JALÓN", "jalon al pecho"))
    }

    @Test
    fun laspalabrasCoincidenEnCualquierOrden() {
        assertTrue(matchesQuery("press mancuerna", "Press de banca con mancuernas"))
        assertTrue(matchesQuery("mancuerna press", "Press de banca con mancuernas"))
        assertFalse(matchesQuery("press sentadilla", "Press de banca con mancuernas"))
    }

    @Test
    fun unaConsultaVaciaCoincideConTodo() {
        assertTrue(matchesQuery("", "Cualquier cosa"))
        assertTrue(matchesQuery("   ", null))
    }

    @Test
    fun elGrupoMuscularTambienSeBusca() {
        assertTrue(matchesQuery("press pecho", "Press de banca", "Pecho"))
        assertTrue(matchesQuery("cuadriceps", "Prensa", "Cuádriceps", null))
        assertFalse(matchesQuery("gemelos", "Prensa", "Cuádriceps", null))
    }
}
