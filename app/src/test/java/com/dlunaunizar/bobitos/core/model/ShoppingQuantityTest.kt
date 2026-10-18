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

    @Test
    fun `the singular of a plural in -es after a vowel and l, r, n, d or j drops the whole -es`() {
        assertEquals("1 bol", ShoppingQuantity.sum("1/2 boles", "1/2 boles"))
        assertEquals("1 pan", ShoppingQuantity.sum("1/2 panes", "1/2 panes"))
        assertEquals("1 yogur", ShoppingQuantity.sum("1/2 yogures", "1/2 yogures"))
        assertEquals("1 botellín", ShoppingQuantity.sum("1/2 botellines", "1/2 botellines"))
        assertEquals("1 balde", ShoppingQuantity.sum("1/2 baldes", "1/2 baldes"))
        assertEquals("1 filete", ShoppingQuantity.sum("1/2 filetes", "1/2 filetes"))
    }

    @Test
    fun `every word of the unit agrees in number up to the first preposition`() {
        assertEquals("1 cucharada sopera", ShoppingQuantity.sum("1/2 cucharadas soperas", "1/2 cucharadas soperas"))
        assertEquals("2 cucharadas soperas", ShoppingQuantity.sum("1 cucharada sopera", "1 cucharada sopera"))
        assertEquals("2 dientes de ajo", ShoppingQuantity.sum("1 diente de ajo", "1 diente de ajo"))
        assertEquals("3 dientes de ajo", ShoppingQuantity.sum("1 diente de ajo", "2 dientes de ajo"))
        assertEquals("1 vaso de leche", ShoppingQuantity.sum("1/2 vasos de leche", "1/2 vasos de leche"))
        assertEquals("1 Cucharada Sopera", ShoppingQuantity.sum("1/2 Cucharadas Soperas.", "1/2 cucharadas soperas"))
    }

    @Test
    fun `abbreviations do not change in number`() {
        assertEquals("3 cl", ShoppingQuantity.sum("1 cl", "2 cl"))
        assertEquals("2 oz", ShoppingQuantity.sum("1 oz", "1 oz"))
        assertEquals("3 mg", ShoppingQuantity.sum("1 mg", "2 mg"))
        assertEquals("2 cc", ShoppingQuantity.sum("1 cc", "1 cc"))
        assertEquals("1 dl", ShoppingQuantity.sum("1/2 dl", "1/2 dl"))
        assertEquals("2 cdas", ShoppingQuantity.sum("1 cda", "1 cda"))
    }

    @Test
    fun `only the words before any preposition of the unit change in number`() {
        assertEquals("2 bolsas con asa", ShoppingQuantity.sum("1 bolsa con asa", "1 bolsa con asa"))
        val half = "1/2 bolsas para galletas"
        assertEquals("1 bolsa para galletas", ShoppingQuantity.sum(half, half))
        assertEquals("2 tazas para café", ShoppingQuantity.sum("1 taza para café", "1 taza para café"))
        assertEquals("2 botes sin azúcar", ShoppingQuantity.sum("1 bote sin azúcar", "1 bote sin azúcar"))
        assertEquals("2 cucharadas al ras", ShoppingQuantity.sum("1 cucharada al ras", "1 cucharada al ras"))
    }

    @Test
    fun `the singular of -ces after a consonant only drops the s`() {
        assertEquals("1 bolsa dulce", ShoppingQuantity.sum("1/2 bolsas dulces", "1/2 bolsas dulces"))
        assertEquals("1 nuez", ShoppingQuantity.sum("1/2 nueces", "1/2 nueces"))
    }

    @Test
    fun `the singular of a plural in -nes gets the accent of its stress`() {
        assertEquals("1 bolsa común", ShoppingQuantity.sum("1/2 bolsas comunes", "1/2 bolsas comunes"))
        assertEquals("1 pan alemán", ShoppingQuantity.sum("1/2 panes alemanes", "1/2 panes alemanes"))
        assertEquals("1 camión", ShoppingQuantity.sum("1/2 camiones", "1/2 camiones"))
        assertEquals("1 examen", ShoppingQuantity.sum("1/2 exámenes", "1/2 exámenes"))
        assertEquals("1 tren", ShoppingQuantity.sum("1/2 trenes", "1/2 trenes"))
        assertEquals("1 guion", ShoppingQuantity.sum("1/2 guiones", "1/2 guiones"))
        assertEquals("1 limón", ShoppingQuantity.sum("1/2 limones", "1/2 limones"))
        assertEquals("1 botellín", ShoppingQuantity.sum("1/2 botellines", "1/2 botellines"))
    }

    @Test
    fun `the singular keeps the capital letters and puts the accent in capitals`() {
        assertEquals("1 PAN ALEMÁN", ShoppingQuantity.sum("1/2 PANES ALEMANES", "1/2 PANES ALEMANES"))
    }

    @Test
    fun `the singular of a diphthong before the consonant keeps the e`() {
        assertEquals("1 peine", ShoppingQuantity.sum("1/2 peines", "1/2 peines"))
        assertEquals("1 baile", ShoppingQuantity.sum("1/2 bailes", "1/2 bailes"))
    }
}
