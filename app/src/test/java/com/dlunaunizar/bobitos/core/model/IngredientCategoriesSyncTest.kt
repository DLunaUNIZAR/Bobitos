package com.dlunaunizar.bobitos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IngredientCategoriesSyncTest {
    // La lista de la app tiene que ser la del catálogo (scripts/catalog/ingredients.mjs), en el mismo orden.
    @Test
    fun `the app categories are the catalog ones in the same order`() {
        val script = listOf(File("../scripts/catalog/ingredients.mjs"), File("scripts/catalog/ingredients.mjs"))
            .firstOrNull(File::exists)
            .let { checkNotNull(it) { "no se encuentra scripts/catalog/ingredients.mjs" } }
            .readText()
        val start = script.indexOf("INGREDIENT_CATEGORIES = [")
        assertTrue("no se encuentra INGREDIENT_CATEGORIES", start >= 0)
        val open = script.indexOf('[', start)
        val close = script.indexOf(']', open)
        // Entre comillas: «Cereales, pasta y arroz» lleva una coma.
        val catalog = Regex("\"([^\"]+)\"").findAll(script.substring(open, close)).map { it.groupValues[1] }.toList()

        assertEquals(catalog, INGREDIENT_CATEGORIES)
    }
}
