package com.dlunaunizar.bobitos.feature.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `badge and skip texts are the ones the spec fixes`() {
        assertEquals("En la despensa", text("meals_review_badge_pantry"))
        assertEquals("En la lista: %1\$s", text("meals_review_badge_listed"))
        assertEquals("En la lista", text("meals_review_badge_listed_plain"))
        assertEquals("Ya comprado", text("meals_review_badge_purchased"))
        assertEquals("No añadir", text("meals_review_skip"))
    }

    @Test
    fun `the skip option is read as No añadir a la lista`() {
        assertEquals("No añadir a la lista", text("meals_review_skip_description"))
    }

    @Test
    fun `the old combined hints are gone`() {
        listOf(
            "meals_review_existing",
            "meals_review_existing_listed",
            "meals_review_existing_recipe",
            "meals_review_existing_plain",
            "meals_review_purchased",
            "meals_review_purchased_recipe",
            "meals_review_in_pantry",
            "meals_review_in_pantry_recipe",
        ).forEach { name -> assertFalse(name, xml.contains("<string name=\"$name\">")) }
    }
}
