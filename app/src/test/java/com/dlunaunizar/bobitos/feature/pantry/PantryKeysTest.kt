package com.dlunaunizar.bobitos.feature.pantry

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryKeysTest {
    @Test
    fun `the Others header key never matches an item whose id is h-null`() {
        assertNotEquals(pantryItemKey("h-null"), pantryHeaderKey(null))
    }

    @Test
    fun `a category header key never matches an item with the same text`() {
        assertNotEquals(pantryItemKey("h-Lácteos"), pantryHeaderKey("Lácteos"))
    }

    @Test
    fun `the free text key never matches an item whose id is free-tomat`() {
        assertNotEquals(pantryItemKey("free-tomat"), pantryFreeTextKey("tomat"))
    }

    @Test
    fun `header and free text keys never match each other`() {
        assertNotEquals(pantryHeaderKey("free-tomat"), pantryFreeTextKey("tomat"))
        assertTrue(pantryHeaderKey(null) != pantryHeaderKey("null"))
    }
}
