package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.core.model.ExerciseSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSourceTokensTest {
    private fun source(url: String?) = ExerciseSource("wger", 1, "CC-BY-SA-4.0", "Ana", url)

    @Test
    fun losCodigosDeLicenciaConocidosTienenEtiquetaYUrl() {
        assertEquals("CC BY-SA 4.0", licenseLabel("CC-BY-SA-4.0"))
        assertEquals("https://creativecommons.org/licenses/by-sa/4.0/deed.es", licenseUrl("CC-BY-SA-4.0"))
        assertEquals("CC BY-SA 3.0", licenseLabel("CC-BY-SA-3.0"))
        assertEquals("https://creativecommons.org/licenses/by-sa/3.0/deed.es", licenseUrl("CC-BY-SA-3.0"))
        assertEquals("CC BY 4.0", licenseLabel("CC-BY-4.0"))
        assertEquals("https://creativecommons.org/licenses/by/4.0/deed.es", licenseUrl("CC-BY-4.0"))
        assertEquals("CC0 1.0", licenseLabel("CC0-1.0"))
        assertEquals("https://creativecommons.org/publicdomain/zero/1.0/deed.es", licenseUrl("CC0-1.0"))
    }

    @Test
    fun unCodigoDesconocidoSeMuestraTalCualYSinUrl() {
        assertEquals("XYZ-9", licenseLabel("XYZ-9"))
        assertNull(licenseUrl("XYZ-9"))
    }

    @Test
    fun soloSeEnlazanLasUrlDeWger() {
        assertEquals("https://wger.de/es/exercise/1/view/", source("https://wger.de/es/exercise/1/view/").linkUrl())
        assertNull(source("http://wger.de/x").linkUrl())
        assertNull(source("https://evil.example/https://wger.de/").linkUrl())
        assertNull(source(null).linkUrl())
    }

    @Test
    fun `bobitos attribution has own wording and no link`() {
        val bobitos = ExerciseSource("bobitos", 7, "CC-BY-SA-4.0", "Catálogo Bobitos", null)
        assertEquals(ExerciseAttribution.Bobitos("CC-BY-SA-4.0"), bobitos.attribution())
        // Aunque llegara una url, no se enlaza ninguna ficha externa.
        assertTrue(bobitos.copy(url = "https://wger.de/x").attribution() is ExerciseAttribution.Bobitos)
        assertEquals(
            ExerciseAttribution.Wger(1, "Ana", "CC-BY-SA-4.0", "https://wger.de/es/exercise/1/view/"),
            source("https://wger.de/es/exercise/1/view/").attribution(),
        )
    }

    @Test
    fun `unknown provider shows provider and licence`() {
        val other = ExerciseSource("acme", 3, "XYZ-9", null, "https://acme.example/3")
        assertEquals(ExerciseAttribution.Other("acme", "XYZ-9"), other.attribution())
    }
}
