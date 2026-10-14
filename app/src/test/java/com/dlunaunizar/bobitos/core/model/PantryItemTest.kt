package com.dlunaunizar.bobitos.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PantryItemTest {
    @Test
    fun `pantryId joins singular and plural, case and accents`() {
        assertEquals("tomat", pantryId("Tomates"))
        assertEquals("tomat", pantryId("  tomate "))
        assertEquals("limon", pantryId("LIMONES"))
    }

    @Test
    fun `pantryId drops stop words and sorts the words of the key`() {
        assertEquals("aceit-oliva", pantryId("Aceite de oliva"))
        assertEquals("cocina-papel", pantryId("Papel de cocina"))
    }

    @Test
    fun `pantryId of a name made only of stop words uses the name`() {
        assertEquals("de", pantryId("De"))
    }

    @Test
    fun `pantryId is empty without letters or numbers`() {
        assertEquals("", pantryId("¡¿!?"))
        assertEquals("", pantryId("   "))
    }
}
