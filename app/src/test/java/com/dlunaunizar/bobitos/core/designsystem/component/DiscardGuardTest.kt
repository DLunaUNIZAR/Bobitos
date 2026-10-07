package com.dlunaunizar.bobitos.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscardGuardTest {
    @Test
    fun sinCambiosCierraDirectamente() {
        assertEquals(DismissAction.CLOSE, dismissActionFor(dirty = false, saving = false))
    }

    @Test
    fun conCambiosPideConfirmacion() {
        assertEquals(DismissAction.ASK_DISCARD, dismissActionFor(dirty = true, saving = false))
    }

    @Test
    fun guardandoNoSeCierraPorGesto() {
        assertEquals(DismissAction.BLOCK, dismissActionFor(dirty = true, saving = true))
        assertEquals(DismissAction.BLOCK, dismissActionFor(dirty = false, saving = true))
    }
}
