package com.dlunaunizar.bobitos.core.designsystem

import com.dlunaunizar.bobitos.core.designsystem.component.DayCascade
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayCascadeTest {
    private val lunes = LocalDate.of(2026, 10, 12)
    private val martes = LocalDate.of(2026, 10, 13)

    @Test
    fun laPrimeraCargaNoCascada() {
        assertFalse(DayCascade().onContentShown(lunes))
    }

    @Test
    fun elMismoDiaOtraVezNoCascada() {
        val cascada = DayCascade()
        cascada.onContentShown(lunes)
        assertFalse(cascada.onContentShown(lunes))
    }

    @Test
    fun otroDiaCascada() {
        val cascada = DayCascade()
        cascada.onContentShown(lunes)
        assertTrue(cascada.onContentShown(martes))
    }

    @Test
    fun volverAlDiaAnteriorCascada() {
        val cascada = DayCascade()
        cascada.onContentShown(lunes)
        cascada.onContentShown(martes)
        assertTrue(cascada.onContentShown(lunes))
    }
}
