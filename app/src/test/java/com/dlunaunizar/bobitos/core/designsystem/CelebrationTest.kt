package com.dlunaunizar.bobitos.core.designsystem

import com.dlunaunizar.bobitos.core.designsystem.component.shouldCelebrate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CelebrationTest {
    @Test
    fun laUltimaPendienteCompletadaPorElUsuarioCelebra() {
        // Quedaba otra pendiente: todavía no hay nada que celebrar.
        assertFalse(shouldCelebrate("t2", pendingBefore = setOf("t1", "t2"), pendingAfter = setOf("t1")))
        assertTrue(shouldCelebrate("t1", pendingBefore = setOf("t1"), pendingAfter = emptySet()))
    }

    @Test
    fun laCargaInicialNoCelebra() {
        assertFalse(shouldCelebrate(null, pendingBefore = setOf("t1"), pendingAfter = emptySet()))
    }

    @Test
    fun unCambioRemotoNoCelebra() {
        // Otro miembro completó la última mientras el usuario no había tocado nada.
        assertFalse(shouldCelebrate(null, pendingBefore = setOf("t1"), pendingAfter = emptySet()))
        // El usuario tocó una que no estaba entre las pendientes de hoy.
        assertFalse(shouldCelebrate("otra", pendingBefore = setOf("t1"), pendingAfter = emptySet()))
    }

    @Test
    fun unaListaQueYaEstabaVaciaNoCelebra() {
        assertFalse(shouldCelebrate("t1", pendingBefore = emptySet(), pendingAfter = emptySet()))
    }
}
