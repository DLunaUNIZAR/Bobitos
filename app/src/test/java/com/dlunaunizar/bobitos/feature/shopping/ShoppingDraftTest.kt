package com.dlunaunizar.bobitos.feature.shopping

import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingDraftTest {
    @Test
    fun unBorradorNuevoEmpiezaVacio() {
        assertEquals(ShoppingDraft("", "", "", null, ""), ShoppingDraft.of(null))
    }
}
