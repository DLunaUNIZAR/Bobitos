package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Supermarket
import org.junit.Assert.assertEquals
import org.junit.Test

class IngredientPrefsParseTest {
    @Test
    fun `parses valid entries`() {
        val raw = mapOf<String, Any?>(
            "tomate" to mapOf("supermarket" to "MERCADONA", "brand" to "Hacendado"),
            "sal" to mapOf("supermarket" to null, "brand" to "Marca"),
            "arroz" to mapOf("supermarket" to "DIA", "brand" to null),
        )

        val result = parseIngredientPrefs(raw)

        assertEquals(IngredientPref(Supermarket.MERCADONA, "Hacendado"), result["tomate"])
        assertEquals(IngredientPref(null, "Marca"), result["sal"])
        assertEquals(IngredientPref(Supermarket.DIA, null), result["arroz"])
    }

    @Test
    fun `null map yields empty`() {
        assertEquals(emptyMap<String, IngredientPref>(), parseIngredientPrefs(null))
    }

    @Test
    fun `empty, malformed and unknown-only entries are dropped`() {
        val raw = mapOf<String, Any?>(
            "empty" to mapOf("supermarket" to null, "brand" to null),
            "bad" to "not a map",
            "unknownSuper" to mapOf("supermarket" to "PLUTO", "brand" to null),
        )

        assertEquals(emptyMap<String, IngredientPref>(), parseIngredientPrefs(raw))
    }

    @Test
    fun `blank brand is dropped but a valid supermarket is kept`() {
        val raw = mapOf<String, Any?>("x" to mapOf("supermarket" to "EROSKI", "brand" to "  "))

        assertEquals(IngredientPref(Supermarket.EROSKI, null), parseIngredientPrefs(raw)["x"])
    }
}
