package com.dlunaunizar.bobitos.feature.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiFilterValueTest {
    private val todas = listOf("a" to "Ana", "b" to "Luis", "c" to "Marta")

    @Test
    fun vacioOTodosEsTodas() {
        assertEquals(MultiFilterValue.All, multiFilterValue(emptySet(), todas))
        assertEquals(MultiFilterValue.All, multiFilterValue(setOf("a", "b", "c"), todas))
    }

    @Test
    fun unoEsSuNombre() {
        assertEquals(MultiFilterValue.One("Luis"), multiFilterValue(setOf("b"), todas))
    }

    @Test
    fun variosEsElNumero() {
        assertEquals(MultiFilterValue.Some(2), multiFilterValue(setOf("a", "c"), todas))
    }
}
