package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Supermarket
import org.junit.Assert.assertEquals
import org.junit.Test

class IngredientPrefsParseTest {
    @Test
    fun `parses stores in order`() {
        val raw = mapOf<String, Any?>(
            "tomate" to mapOf("stores" to listOf("MERCADONA", "DIA")),
            "sal" to mapOf("stores" to listOf("EROSKI")),
        )

        val result = parseIngredientPrefs(raw)

        assertEquals(IngredientPref(listOf(Supermarket.MERCADONA, Supermarket.DIA)), result["tomate"])
        assertEquals(IngredientPref(listOf(Supermarket.EROSKI)), result["sal"])
    }

    @Test
    fun `old supermarket and brand entries are ignored`() {
        val raw = mapOf<String, Any?>(
            "tomate" to mapOf("supermarket" to "MERCADONA", "brand" to "Hacendado"),
            "bad" to "not a map",
        )

        assertEquals(emptyMap<String, IngredientPref>(), parseIngredientPrefs(raw))
    }

    @Test
    fun `unknown, INDIFERENTE and duplicate stores are dropped and empty entries removed`() {
        val raw = mapOf<String, Any?>(
            "a" to mapOf("stores" to listOf("PLUTO", "DIA", "INDIFERENTE", "DIA", 7)),
            "empty" to mapOf("stores" to emptyList<String>()),
            "onlyBad" to mapOf("stores" to listOf("PLUTO", "INDIFERENTE")),
        )

        assertEquals(mapOf("a" to IngredientPref(listOf(Supermarket.DIA))), parseIngredientPrefs(raw))
    }

    @Test
    fun `null map yields empty`() {
        assertEquals(emptyMap<String, IngredientPref>(), parseIngredientPrefs(null))
    }
}
