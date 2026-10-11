package com.dlunaunizar.bobitos.core.model

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IngredientKeyTest {
    // La misma tabla que usa tests/catalog-ingredients.test.mjs: las dos versiones tienen que dar lo mismo.
    private val cases = File("../tests/fixtures/ingredient-keys.json").also {
        assertTrue("falta la tabla compartida en ${it.absolutePath}", it.exists())
    }.readText().let(::JSONArray)

    @Test
    fun `ingredientKey matches the table shared with Node`() {
        for (i in 0 until cases.length()) {
            val case = cases.getJSONArray(i)
            assertEquals(case.getString(0), case.getString(1), ingredientKey(case.getString(0)))
        }
    }

    @Test
    fun `different ingredients keep different keys`() {
        assertTrue(ingredientKey("Leche entera") != ingredientKey("Leche semidesnatada"))
        assertTrue(ingredientKey("Nuez moscada") != ingredientKey("Nueces"))
    }
}
