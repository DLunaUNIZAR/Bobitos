package com.dlunaunizar.bobitos.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeShareUrlTest {
    @Test
    fun `extracts a bare url`() {
        assertEquals("https://example.com/receta", RecipeShareUrl.from("https://example.com/receta"))
    }

    @Test
    fun `extracts the url from a sentence`() {
        assertEquals(
            "https://example.com/receta",
            RecipeShareUrl.from("Mira esta receta https://example.com/receta ¡está genial!"),
        )
    }

    @Test
    fun `trims trailing punctuation`() {
        assertEquals(
            "https://example.com/receta",
            RecipeShareUrl.from("Receta (https://example.com/receta)."),
        )
    }

    @Test
    fun `ignores non-https urls`() {
        assertNull(RecipeShareUrl.from("http://example.com/receta"))
    }

    @Test
    fun `returns null when there is no url`() {
        assertNull(RecipeShareUrl.from("solo texto sin enlace"))
        assertNull(RecipeShareUrl.from(null))
        assertNull(RecipeShareUrl.from("   "))
    }
}
