package com.dlunaunizar.bobitos.core.designsystem.theme

import org.junit.Assert.assertTrue
import org.junit.Test

class WeekDayStripContrastTest {
    private fun paraCadaModuloYTema(comprobar: (AppModule, Boolean) -> Unit) {
        for (dark in listOf(false, true)) AppModule.entries.forEach { comprobar(it, dark) }
    }

    @Test
    fun elDiaElegidoSeDistingueDeLaFranja() = paraCadaModuloYTema { module, dark ->
        val (relleno, _) = ModulePalette.selectedDay(module, dark)
        val franja = ModulePalette.tint(module, dark)
        assertTrue("$module dark=$dark: ${contrast(relleno, franja)}", contrast(relleno, franja) >= 3.0)
    }

    @Test
    fun elTextoDelDiaElegidoSeLee() = paraCadaModuloYTema { module, dark ->
        val (relleno, texto) = ModulePalette.selectedDay(module, dark)
        assertTrue("$module dark=$dark: ${contrast(texto, relleno)}", contrast(texto, relleno) >= 4.5)
    }
}
