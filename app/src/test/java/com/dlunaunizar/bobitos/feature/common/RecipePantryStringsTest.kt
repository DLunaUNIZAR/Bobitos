package com.dlunaunizar.bobitos.feature.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class RecipePantryStringsTest {
    private val xml = File("src/main/res/values/strings.xml").readText()

    private fun text(name: String) = Regex("""<string name="$name">(.*?)</string>""").find(xml)!!.groupValues[1]

    @Test
    fun `the lines with e only change the conjunction`() {
        assertEquals("Faltan 2: %1\$s e %2\$s", text("recipes_pantry_missing_two_e"))
        assertEquals("Faltan 3: %1\$s, %2\$s e %3\$s", text("recipes_pantry_missing_three_e"))
    }
}
