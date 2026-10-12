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
        assertEquals("0,67", ShoppingQuantity.sum("1/3", "1/3"))
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
    fun `a rest that is not plain words is not a unit and is joined`() {
        assertEquals("2-3 dientes + 2-3 dientes", ShoppingQuantity.sum("2-3 dientes", "2-3 dientes"))
        assertEquals("1 taza + 1 ½ taza", ShoppingQuantity.sum("1 taza", "1 ½ taza"))
        assertEquals("1 1/2 taza + 1 1/2 taza", ShoppingQuantity.sum("1 1/2 taza", "1 1/2 taza"))
        assertEquals("1,2,3 + 1,2,3", ShoppingQuantity.sum("1,2,3", "1,2,3"))
        assertEquals("2 x 200 g + 2 x 200 g", ShoppingQuantity.sum("2 x 200 g", "2 x 200 g"))
    }

    @Test
    fun `a trailing dot on the unit is dropped`() {
        assertEquals("500 g", ShoppingQuantity.sum("200 g.", "300 g"))
        assertEquals("3 cdas", ShoppingQuantity.sum("1 cda.", "2 cdas."))
    }

    @Test
    fun `a unit with no letters of its own key does not match no unit`() {
        assertEquals("2 + 3 de", ShoppingQuantity.sum("2", "3 de"))
    }

    @Test
    fun `a dot followed by exactly three digits is ambiguous and joined`() {
        assertEquals("1.500 g + 500 g", ShoppingQuantity.sum("1.500 g", "500 g"))
        assertEquals("0,5 kg", ShoppingQuantity.sum("0.25 kg", "0,25 kg"))
        assertEquals("1,75 kg", ShoppingQuantity.sum("1,500 kg", "0,25 kg"))
    }

    @Test
    fun `malformed quantities never fail and are joined`() {
        assertEquals("1/0 + 1/0", ShoppingQuantity.sum("1/0", "1/0"))
        assertEquals("1,2,3 + 1", ShoppingQuantity.sum("1,2,3", "1"))
        assertEquals("½ + 1", ShoppingQuantity.sum("½", "1"))
        assertEquals("un puñado + un puñado", ShoppingQuantity.sum("un puñado", "un puñado"))
    }

    @Test
    fun `terms joined with plus are grouped by unit`() {
        assertEquals("500 g + 1 ud", ShoppingQuantity.sum("200 g + 1 ud", "300 g"))
    }

    @Test
    fun `unread terms keep their place while read ones are grouped`() {
        assertEquals("2-3 dientes + 3 ud", ShoppingQuantity.sum("2-3 dientes + 1 ud", "2 ud"))
    }

    @Test
    fun `a group of one term is written literally`() {
        assertEquals("1/2 taza + 200 g", ShoppingQuantity.sum("1/2 taza", "200 g"))
    }

    @Test
    fun `fractions are rounded only at the end`() {
        assertEquals("0,25 taza", ShoppingQuantity.sum("1/8 taza", "1/8 taza"))
        assertEquals("1", ShoppingQuantity.sum(ShoppingQuantity.sum("1/3", "1/3"), "1/3"))
    }

    @Test
    fun `units outside the basic ones follow the Spanish plural`() {
        assertEquals("2 packs", ShoppingQuantity.sum("1 pack", "1 pack"))
        assertEquals("2 nueces", ShoppingQuantity.sum("1 nuez", "1 nuez"))
        assertEquals("2 limones", ShoppingQuantity.sum("1 limón", "1 limón"))
        assertEquals("2 sobres", ShoppingQuantity.sum("1 sobre", "1 sobre"))
        assertEquals("3 terrones", ShoppingQuantity.sum("1 terrón", "2 terrón"))
    }

    @Test
    fun `a total of one or less goes back to the singular when only the plural was written`() {
        assertEquals("1 cucharada", ShoppingQuantity.sum("1/2 cucharadas", "1/2 cucharadas"))
        assertEquals("1 nuez", ShoppingQuantity.sum("1/2 nueces", "1/2 nueces"))
        assertEquals("1 limón", ShoppingQuantity.sum("1/2 limones", "1/2 limones"))
        assertEquals("1 sobre", ShoppingQuantity.sum("1/2 sobres", "1/2 sobres"))
    }
}
