package com.dlunaunizar.bobitos.feature.spaces

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class MyDayStringsTest {
    private val keys = listOf(
        "meals_cook_value",
    )

    @Test
    fun noMyDayStringStartsWithAnEmoji() {
        val xml = File("src/main/res/values/strings.xml").readText()
        keys.forEach { key ->
            val value = Regex("""<string name="$key">(.*?)</string>""").find(xml)!!.groupValues[1]
            val hasEmoji = value.codePoints().anyMatch { it >= 0x1F000 || it in 0x2600..0x27BF }
            assertFalse("$key tiene emoji: $value", hasEmoji)
        }
    }
}
