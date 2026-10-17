package com.dlunaunizar.bobitos.feature.spaces

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class MyDayStringsTest {
    private val keys = listOf(
        "home_my_day_title",
        "home_my_day_tasks",
        "home_my_day_cooking",
        "home_my_day_eating",
        "home_my_day_empty",
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
