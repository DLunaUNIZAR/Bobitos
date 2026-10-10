package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.core.model.ExerciseImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseImageCreditTest {
    private val hash = "a".repeat(64)

    @Test
    fun `credit mentions adaptation and falls back to wger contributors`() {
        assertEquals("colaboradores de wger", ExerciseImage(hash, null, "CC-BY-SA-4.0", null).credit().author)
        assertEquals("colaboradores de wger", ExerciseImage(hash, "  ", "CC-BY-SA-4.0", null).credit().author)
        assertEquals("Ana", ExerciseImage(hash, "Ana", "CC-BY-SA-4.0", null).credit().author)
    }

    @Test
    fun `credit maps licence label and deed url`() {
        val credit = ExerciseImage(hash, "Ana", "CC-BY-SA-4.0", null).credit()
        assertEquals(ImageCredit("Ana", "CC BY-SA 4.0", CC_BY_SA_4_URL), credit)
    }

    @Test
    fun `unknown licence has no url`() {
        val credit = ExerciseImage(hash, "Ana", "Otra-1.0", null).credit()
        assertEquals("Otra-1.0", credit.licenseLabel)
        assertNull(credit.licenseUrl)
    }

    @Test
    fun `credit text mentions the adaptation after the licence`() {
        val xml = java.io.File("src/main/res/values/strings.xml").readText()
        val suffix = Regex("""<string name="exercises_image_credit_suffix">(.*?)</string>""").find(xml)!!.groupValues[1]
        assertEquals("\" · adaptada de wger.de\"", suffix)
    }
}
