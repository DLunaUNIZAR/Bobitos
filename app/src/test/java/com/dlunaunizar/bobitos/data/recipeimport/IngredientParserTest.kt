package com.dlunaunizar.bobitos.data.recipeimport

import com.dlunaunizar.bobitos.core.model.Ingredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IngredientParserTest {
    @Test
    fun `splits quantity unit and name`() {
        assertEquals(Ingredient("arroz", "300", "g"), IngredientParser.parse("300 g de arroz"))
    }

    @Test
    fun `keeps quantity without a known unit`() {
        assertEquals(Ingredient("huevos", "2", null), IngredientParser.parse("2 huevos"))
    }

    @Test
    fun `handles fractions`() {
        assertEquals(Ingredient("sal", "1/2", "cucharadita"), IngredientParser.parse("1/2 cucharadita de sal"))
    }

    @Test
    fun `handles unicode fractions`() {
        assertEquals(Ingredient("azúcar", "½", "taza"), IngredientParser.parse("½ taza de azúcar"))
    }

    @Test
    fun `handles ranges without a known unit`() {
        assertEquals(Ingredient("manzanas", "2-3", null), IngredientParser.parse("2-3 manzanas"))
    }

    @Test
    fun `pluralized unit is recognized`() {
        assertEquals(Ingredient("ajo", "2", "dientes"), IngredientParser.parse("2 dientes de ajo"))
    }

    @Test
    fun `no quantity keeps the whole line as name`() {
        assertEquals(Ingredient("Sal al gusto", null, null), IngredientParser.parse("Sal al gusto"))
    }

    @Test
    fun `collapses inner whitespace`() {
        assertEquals(Ingredient("arroz", "300", "g"), IngredientParser.parse("  300   g   de   arroz "))
    }

    @Test
    fun `blank line yields null`() {
        assertNull(IngredientParser.parse("   "))
    }

    @Test
    fun `degenerate line without a name is kept intact`() {
        assertEquals(Ingredient("2 tazas"), IngredientParser.parse("2 tazas"))
    }
}
