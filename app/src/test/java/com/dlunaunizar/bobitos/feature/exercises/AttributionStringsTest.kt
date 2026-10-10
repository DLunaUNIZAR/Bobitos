package com.dlunaunizar.bobitos.feature.exercises

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * aapt2 recorta los espacios iniciales y finales de un `<string>` sin comillas; los valores con espacios
 * significativos deben ir entre comillas dobles. Se emula esa regla sobre el strings.xml real.
 */
class AttributionStringsTest {
    private fun aaptValue(name: String): String {
        val xml = File("src/main/res/values/strings.xml").readText()
        val raw = Regex("""<string name="$name">(.*?)</string>""").find(xml)!!.groupValues[1]
        val quoted = raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")
        return if (quoted) raw.substring(1, raw.length - 1) else raw.trim()
    }

    @Test
    fun elPrefijoConservaElEspacioFinal() {
        assertTrue(aaptValue("exercises_attribution_prefix").endsWith(" "))
    }

    @Test
    fun laAutoriaConservaElEspacioInicial() {
        assertTrue(aaptValue("exercises_attribution_author").startsWith(" "))
    }

    @Test
    fun laLicenciaConservaEspaciosEnAmbosExtremos() {
        val v = aaptValue("exercises_attribution_license")
        assertTrue(v.startsWith(" ") && v.endsWith(" "))
    }
}
