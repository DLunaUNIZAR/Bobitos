package com.dlunaunizar.bobitos.core.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecipeUnitsFixtureTest {
    // La misma tabla que scripts/catalog/recipes.mjs (RECIPE_UNITS): las cantidades de las recetas del catálogo se
    // suman al «Añadir a la compra» dos veces y la unidad concuerda en número con el total.
    private val units = File("../tests/fixtures/recipe-units.json").also {
        assertTrue("falta la tabla compartida en ${it.absolutePath}", it.exists())
    }.readText().let(::JSONObject)

    @Test
    fun `abbreviated units add up and do not change`() {
        val invariable = units.getJSONArray("invariable")
        for (i in 0 until invariable.length()) {
            val unit = invariable.getString(i)
            assertEquals(unit, "3 $unit", ShoppingQuantity.sum("1 $unit", "2 $unit"))
        }
    }

    @Test
    fun `counted units add up and agree in number with the total`() {
        val counted = units.getJSONArray("counted")
        for (i in 0 until counted.length()) {
            val singular = counted.getJSONObject(i).getString("singular")
            val plural = counted.getJSONObject(i).getString("plural")
            assertEquals(singular, "3 $plural", ShoppingQuantity.sum("1 $singular", "2 $plural"))
            assertEquals(singular, "3 $plural", ShoppingQuantity.sum("2 $plural", "1 $singular"))
            assertEquals(singular, "2 $plural", ShoppingQuantity.sum("1 $singular", "1 $singular"))
            assertEquals(singular, "1 $singular", ShoppingQuantity.sum("1/2 $singular", "1/2 $singular"))
        }
    }
}
