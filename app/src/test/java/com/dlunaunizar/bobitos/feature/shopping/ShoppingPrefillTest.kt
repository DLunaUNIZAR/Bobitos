package com.dlunaunizar.bobitos.feature.shopping

import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingPrefillTest {
    @Test
    fun `a prefill waits while the list cannot be written`() {
        assertEquals(PrefillAction.Wait, prefillAction("Tomate", canWrite = false))
    }

    @Test
    fun `a prefill opens when it can be written`() {
        assertEquals(PrefillAction.Open("Tomate"), prefillAction("Tomate", canWrite = true))
    }

    @Test
    fun `no prefill does nothing`() {
        assertEquals(PrefillAction.None, prefillAction(null, canWrite = true))
        assertEquals(PrefillAction.None, prefillAction(null, canWrite = false))
    }
}
