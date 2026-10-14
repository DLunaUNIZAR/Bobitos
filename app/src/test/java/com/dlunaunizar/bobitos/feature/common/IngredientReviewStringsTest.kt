package com.dlunaunizar.bobitos.feature.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class IngredientReviewStringsTest {
    private val xml = File("src/main/res/values/strings.xml").readText()

    private fun text(name: String) = Regex("""<string name="$name">(.*?)</string>""").find(xml)!!.groupValues[1]

    @Test
    fun `with every row omitted the notice says nothing needs adding`() {
        assertEquals("No hace falta añadir nada", text("meals_notice_nothing_to_add"))
        assertEquals("No hace falta añadir nada", text("recipes_notice_nothing_to_add"))
    }
}
