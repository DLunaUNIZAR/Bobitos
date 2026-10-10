package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.core.model.ExerciseImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseImageCreditTest {
    private val url = "https://wger.de/media/exercise-images/1/a.png"

    @Test
    fun `credit falls back to wger contributors`() {
        assertEquals("colaboradores de wger", ExerciseImage(url, null, "CC-BY-SA-4.0").credit().author)
        assertEquals("colaboradores de wger", ExerciseImage(url, "  ", "CC-BY-SA-4.0").credit().author)
        assertEquals("Ana", ExerciseImage(url, "Ana", "CC-BY-SA-4.0").credit().author)
    }

    @Test
    fun `credit maps licence label and deed url`() {
        val credit = ExerciseImage(url, "Ana", "CC-BY-SA-4.0").credit()
        assertEquals(ImageCredit("Ana", "CC BY-SA 4.0", CC_BY_SA_4_URL), credit)
    }

    @Test
    fun `unknown licence has no url`() {
        val credit = ExerciseImage(url, "Ana", "Otra-1.0").credit()
        assertEquals("Otra-1.0", credit.licenseLabel)
        assertNull(credit.licenseUrl)
    }
}
