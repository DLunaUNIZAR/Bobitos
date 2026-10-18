package com.dlunaunizar.bobitos.core.designsystem

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test
    fun conQuitarAnimacionesLaEspecificacionEsInstantanea() {
        assertTrue(motionSpec(true, spring<Float>()) is SnapSpec<*>)
    }

    @Test
    fun sinQuitarAnimacionesSeRespeta() {
        val spec = tween<Float>(100)
        assertEquals(spec, motionSpec(false, spec))
    }

    @Test
    fun entrePestanasEsFundidoYAlEntrarEnUnaPantallaEsDeslizar() {
        assertEquals(NavMotion.FADE_THROUGH, navMotionFor("tasks", "shopping"))
        assertEquals(NavMotion.SLIDE, navMotionFor("space-more", "recipes"))
        assertEquals(NavMotion.SLIDE, navMotionFor("recipes", "space-more"))
    }

    @Test
    fun elFabSeRecogeAlBajarYVuelveAlSubir() {
        // Arriba del todo: extendido, aunque estuviera recogido.
        assertTrue(fabExpandedAfterScroll(false, 1, 10, 0, 0))
        // Bajando: se recoge.
        assertFalse(fabExpandedAfterScroll(true, 0, 50, 1, 10))
        assertFalse(fabExpandedAfterScroll(true, 2, 5, 2, 30))
        // Subiendo: vuelve.
        assertTrue(fabExpandedAfterScroll(false, 3, 10, 2, 40))
        assertTrue(fabExpandedAfterScroll(false, 2, 30, 2, 5))
        // Quieto: mantiene.
        assertFalse(fabExpandedAfterScroll(false, 2, 30, 2, 30))
        assertTrue(fabExpandedAfterScroll(true, 2, 30, 2, 30))
    }
}
