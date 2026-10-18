package com.dlunaunizar.bobitos.core.designsystem

import com.dlunaunizar.bobitos.core.designsystem.component.CelebrationState
import com.dlunaunizar.bobitos.core.designsystem.component.shouldCelebrate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CelebrationTest {
    @Test
    fun laUltimaPendienteCompletadaPorElUsuarioCelebra() {
        // Quedaba otra pendiente: todavía no hay nada que celebrar.
        assertFalse(shouldCelebrate(setOf("t2"), pendingBefore = setOf("t1", "t2"), pendingAfter = setOf("t1")))
        assertTrue(shouldCelebrate(setOf("t1"), pendingBefore = setOf("t1"), pendingAfter = emptySet()))
    }

    @Test
    fun laCargaInicialNoCelebra() {
        assertFalse(shouldCelebrate(emptySet(), pendingBefore = setOf("t1"), pendingAfter = emptySet()))
    }

    @Test
    fun unCambioRemotoNoCelebra() {
        // Otro miembro completó la última mientras el usuario no había tocado nada.
        assertFalse(shouldCelebrate(emptySet(), pendingBefore = setOf("t1"), pendingAfter = emptySet()))
        // El usuario tocó una que no estaba entre las pendientes de hoy.
        assertFalse(shouldCelebrate(setOf("otra"), pendingBefore = setOf("t1"), pendingAfter = emptySet()))
    }

    @Test
    fun unaListaQueYaEstabaVaciaNoCelebra() {
        assertFalse(shouldCelebrate(setOf("t1"), pendingBefore = emptySet(), pendingAfter = emptySet()))
    }

    @Test
    fun variasSeguidasCelebranAlLlegarLaUltimaConfirmacion() {
        val state = CelebrationState()
        state.onPending(setOf("a", "b"))

        state.userCompleted("a")
        state.userCompleted("b")
        // Llega la confirmación de A con B todavía pendiente, y luego la de B.
        state.onPending(setOf("b"))
        assertFalse(state.visible)
        state.onPending(emptySet())

        assertTrue(state.visible)
    }

    @Test
    fun unaEmisionSinCambiosDeTareasNoBorraLosToques() {
        val state = CelebrationState()
        state.onPending(setOf("a", "b"))

        state.userCompleted("a")
        state.userCompleted("b")
        // Otra emisión de la agenda (compra, miembros) con las mismas pendientes.
        state.onPending(setOf("a", "b"))
        state.onPending(setOf("b"))
        state.onPending(emptySet())

        assertTrue(state.visible)
    }

    @Test
    fun unaEscrituraQueFallaNoCuentaParaCelebrar() {
        val state = CelebrationState()
        state.onPending(setOf("a", "b"))

        state.userCompleted("a")
        state.userCancelled("a")
        // Un miembro remoto completa las dos: el toque de A ya no vale.
        state.onPending(emptySet())

        assertFalse(state.visible)
    }

    @Test
    fun laPrimeraListaNoCelebra() {
        val state = CelebrationState()

        state.userCompleted("a")
        state.onPending(emptySet())

        assertFalse(state.visible)
    }
}
