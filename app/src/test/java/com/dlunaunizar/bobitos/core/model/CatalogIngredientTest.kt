package com.dlunaunizar.bobitos.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogIngredientTest {
    @Test
    fun `slug lowercases and joins words with hyphen`() {
        assertEquals("tomate-frito", slug("Tomate frito"))
    }

    @Test
    fun `slug collapses whitespace and trims`() {
        assertEquals("aceite-de-oliva", slug("  Aceite  de   Oliva "))
    }

    @Test
    fun `slug strips accents and tilde`() {
        assertEquals("jamon-serrano", slug("Jamón Serrano"))
        assertEquals("noquis", slug("Ñoquis"))
        assertEquals("cafe", slug("Café"))
    }

    @Test
    fun `slug turns punctuation into single hyphens without leading or trailing`() {
        assertEquals("50-cacao", slug("50% cacao!"))
        assertEquals("sal", slug("  ¡Sal!  "))
    }

    @Test
    fun `slug of blank or symbol-only is empty`() {
        assertEquals("", slug("   "))
        assertEquals("", slug("—··—"))
    }
}
