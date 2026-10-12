package com.dlunaunizar.bobitos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShoppingQuantityTest {
    @Test
    fun `numbers with the same unit are added`() {
        assertEquals("500 g", ShoppingQuantity.sum("200 g", "300 g"))
        assertEquals("3", ShoppingQuantity.sum("1", "2"))
        assertEquals("2,5 kg", ShoppingQuantity.sum("1,5 kg", "1 kg"))
        assertEquals("2 l", ShoppingQuantity.sum("1.5 l", "0,5 litros"))
        assertEquals("500 g", ShoppingQuantity.sum("200g", "300 g"))
    }

    @Test
    fun `basic units are written in their short form`() {
        assertEquals("500 g", ShoppingQuantity.sum("200 gramos", "300 g"))
        assertEquals("3 ud", ShoppingQuantity.sum("1 unidad", "2 uds"))
        assertEquals("2 kg", ShoppingQuantity.sum("1 Kg", "1 kilo"))
    }

    @Test
    fun `other units agree in number with the total`() {
        assertEquals("3 dientes", ShoppingQuantity.sum("1 diente", "2 dientes"))
        assertEquals("2 dientes", ShoppingQuantity.sum("1 diente", "1 diente"))
        assertEquals("3 cucharadas", ShoppingQuantity.sum("2 cucharadas", "1 cucharada"))
        assertEquals("1 cucharadita", ShoppingQuantity.sum("1/2 cucharadita", "1/2 cucharadita"))
    }

    @Test
    fun `fractions are added with two decimals`() {
        assertEquals("0,75", ShoppingQuantity.sum("1/2", "1/4"))
        assertEquals("0,66", ShoppingQuantity.sum("1/3", "1/3"))
    }

    @Test
    fun `different units are joined, not converted`() {
        assertEquals("200 g + 1 kg", ShoppingQuantity.sum("200 g", "1 kg"))
        assertEquals("2 + 1 ud", ShoppingQuantity.sum("2", "1 ud"))
        assertEquals("200 + 100 g", ShoppingQuantity.sum("200", "100 g"))
    }

    @Test
    fun `a missing quantity leaves the other one`() {
        assertEquals("200 g", ShoppingQuantity.sum(null, "200 g"))
        assertEquals("1 kg", ShoppingQuantity.sum(" 1 kg ", null))
        assertEquals("1 kg", ShoppingQuantity.sum("1 kg", "  "))
        assertNull(ShoppingQuantity.sum(null, " "))
        assertNull(ShoppingQuantity.sum("", null))
    }

    @Test
    fun `malformed quantities never fail and are joined`() {
        assertEquals("1/0 + 1/0", ShoppingQuantity.sum("1/0", "1/0"))
        assertEquals("1,2,3 + 1", ShoppingQuantity.sum("1,2,3", "1"))
        assertEquals("½ + 1", ShoppingQuantity.sum("½", "1"))
        assertEquals("un puñado + un puñado", ShoppingQuantity.sum("un puñado", "un puñado"))
    }
}
