package com.dlunaunizar.bobitos.data.recipeimport

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeMetaParserTest {
    @Test
    fun `recipeYield gives its first whole number between 1 and 50`() {
        assertEquals(4, RecipeMetaParser.servings(4))
        assertEquals(4, RecipeMetaParser.servings(4.0))
        assertEquals(4, RecipeMetaParser.servings("4 raciones"))
        assertEquals(6, RecipeMetaParser.servings("Para 6-8 personas"))
        assertEquals(2, RecipeMetaParser.servings(JSONArray(listOf("2", "2 raciones"))))
        assertEquals(3, RecipeMetaParser.servings(JSONArray(listOf("unas cuantas", "3 personas"))))
        assertNull(RecipeMetaParser.servings("unas cuantas"))
        assertNull(RecipeMetaParser.servings("100 galletas"))
        assertNull(RecipeMetaParser.servings("99999999999999999999 raciones"))
        assertNull(RecipeMetaParser.servings(0))
        assertNull(RecipeMetaParser.servings(null))
    }

    @Test
    fun `iso 8601 durations become minutes`() {
        assertEquals(90L, RecipeMetaParser.isoMinutes("PT1H30M"))
        assertEquals(45L, RecipeMetaParser.isoMinutes("PT45M"))
        assertEquals(60L, RecipeMetaParser.isoMinutes("P0DT1H"))
        assertEquals(1440L, RecipeMetaParser.isoMinutes("P1D"))
        assertEquals(45L, RecipeMetaParser.isoMinutes("P0Y0M0DT0H45M0.000S"))
        assertEquals(20L, RecipeMetaParser.isoMinutes(" pt20m "))
        assertEquals(1L, RecipeMetaParser.isoMinutes("PT90S"))
        assertNull(RecipeMetaParser.isoMinutes("P1Y"))
        assertNull(RecipeMetaParser.isoMinutes("P2W"))
        assertNull(RecipeMetaParser.isoMinutes("PT"))
        assertNull(RecipeMetaParser.isoMinutes("45 min"))
        assertNull(RecipeMetaParser.isoMinutes(""))
        assertNull(RecipeMetaParser.isoMinutes(null))
    }

    @Test
    fun `total time falls back to prep plus cook and stays within a day`() {
        assertEquals(90, RecipeMetaParser.totalMinutes("PT1H30M", "PT10M", "PT20M"))
        assertEquals(45, RecipeMetaParser.totalMinutes(null, "PT15M", "PT30M"))
        assertEquals(30, RecipeMetaParser.totalMinutes("", null, "PT30M"))
        assertEquals(30, RecipeMetaParser.totalMinutes("PT0M", "PT10M", "PT20M"))
        assertEquals(30, RecipeMetaParser.totalMinutes("45 minutos", "PT10M", "PT20M"))
        assertNull(RecipeMetaParser.totalMinutes("P2D", null, null))
        assertNull(RecipeMetaParser.totalMinutes("PT0M", null, null))
        assertNull(RecipeMetaParser.totalMinutes(null, null, null))
    }

    @Test
    fun `a long step is split by sentences without losing text`() {
        val sentence = "Remueve la mezcla con cuidado durante un buen rato hasta que espese."
        val long = List(10) { sentence }.joinToString(" ")

        val steps = RecipeMetaParser.steps(listOf(long))

        assertTrue(steps.size > 1)
        assertTrue(steps.all { it.length <= 500 })
        assertEquals(long, steps.joinToString(" "))
    }

    @Test
    fun `a sentence longer than a step is cut in pieces of 500`() {
        val word = "a".repeat(1200)

        val steps = RecipeMetaParser.steps(listOf(word))

        assertEquals(listOf(500, 500, 200), steps.map(String::length))
        assertEquals(word, steps.joinToString(""))
    }

    @Test
    fun `steps are cleaned and capped at 30`() {
        val steps = RecipeMetaParser.steps(listOf("  Uno ", "", "Dos") + List(40) { "Paso $it" })

        assertEquals(30, steps.size)
        assertEquals(listOf("Uno", "Dos"), steps.take(2))
    }
}
