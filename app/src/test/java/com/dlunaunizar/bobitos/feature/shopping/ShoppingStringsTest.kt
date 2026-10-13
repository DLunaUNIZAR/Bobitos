package com.dlunaunizar.bobitos.feature.shopping

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShoppingStringsTest {
    @Test
    fun elAvisoDeRepetidoNoPermiteParTrasCantidad() {
        val xml = File("src/main/res/values/strings.xml").readText()
        val body = Regex("""<string name="shopping_duplicate_body">(.*?)</string>""").find(xml)!!.groupValues[1]
        assertTrue(body, body.contains("cantidad: %2\$s"))
    }
}
