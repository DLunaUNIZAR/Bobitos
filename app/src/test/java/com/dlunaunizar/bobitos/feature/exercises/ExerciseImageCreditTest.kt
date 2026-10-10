package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.data.repository.LoadedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseImageCreditTest {
    private val bytes = byteArrayOf(1)

    private fun loaded(author: String?, license: String? = "CC-BY-SA-4.0") = LoadedImage(bytes, author, license, null)

    @Test
    fun `credit mentions adaptation and falls back to wger contributors`() {
        assertEquals("colaboradores de wger", loaded(null, "CC-BY-SA-4.0").credit("CC0-1.0").author)
        assertEquals("colaboradores de wger", loaded("  ", "CC-BY-SA-4.0").credit("CC0-1.0").author)
        assertEquals("Ana", loaded("Ana", "CC-BY-SA-4.0").credit("CC0-1.0").author)
    }

    @Test
    fun `credit comes from the loaded document and falls back to the sheet licence when it has none`() {
        val credit = loaded("Eva", "CC-BY-4.0").credit("CC-BY-SA-4.0")
        assertEquals(ImageCredit("Eva", "CC BY 4.0", credit.licenseUrl), credit)
        assertEquals("CC BY-SA 4.0", loaded("Eva", null).credit("CC-BY-SA-4.0").licenseLabel)
    }

    @Test
    fun `credit maps licence label and deed url`() {
        val credit = loaded("Ana", "CC-BY-SA-4.0").credit("CC0-1.0")
        assertEquals(ImageCredit("Ana", "CC BY-SA 4.0", CC_BY_SA_4_URL), credit)
    }

    @Test
    fun `unknown licence has no url`() {
        val credit = loaded("Ana", "Otra-1.0").credit("CC0-1.0")
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
