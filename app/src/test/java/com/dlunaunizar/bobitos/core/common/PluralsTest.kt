package com.dlunaunizar.bobitos.core.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PluralsTest {
    private val xml = File("src/main/res/values/strings.xml").readText()

    private val names = listOf(
        "routines_exercise_count",
        "routines_summary_strength",
        "recipes_pantry_missing_more",
        "pantry_picker_confirm",
        "recurrence_every_n_days",
        "recurrence_every_n_weeks",
        "recurrence_every_n_months",
        "home_workload_pending",
        "home_workload_done",
    )

    @Test
    fun `counts are plurals with one and other`() {
        names.forEach { name ->
            val body = Regex("""<plurals name="$name">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
                .find(xml)?.groupValues?.get(1)
            assertTrue("falta <plurals name=\"$name\">", body != null)
            assertTrue("$name sin one", body!!.contains("""quantity="one""""))
            assertTrue("$name sin other", body.contains("""quantity="other""""))
            assertTrue("$name sigue como string", !xml.contains("""<string name="$name">"""))
        }
    }

    @Test
    fun `workload counts string is gone`() {
        assertTrue(!xml.contains("home_workload_counts"))
    }
}
