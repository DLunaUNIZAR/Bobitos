package com.dlunaunizar.bobitos.feature.pantry

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.PantryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryPickerLogicTest {
    private val ingredients = listOf(
        CatalogIngredient("tomate", "Tomate", "Verduras y hortalizas"),
        CatalogIngredient("sal", "Sal", "Especias y hierbas"),
        CatalogIngredient("manzana", "Manzana", "Frutas"),
    )

    @Test
    fun `without a query every ingredient shows grouped by category`() {
        val content = pantryPickerContent(ingredients, emptySet(), "")

        val categories = content.groups.map { it.category }
        assertEquals(listOf("Frutas", "Verduras y hortalizas", "Especias y hierbas"), categories)
        assertNull(content.freeText)
    }

    @Test
    fun `what is already in the pantry is marked`() {
        val rows = pantryPickerContent(ingredients, setOf("tomat"), "").groups.flatMap { it.items }

        assertEquals(listOf("Tomate"), rows.filter { it.inPantry }.map { it.entry.name })
    }

    @Test
    fun `searching by category finds its ingredients`() {
        val rows = pantryPickerContent(ingredients, emptySet(), "frutas").groups.flatMap { it.items }

        assertEquals(listOf("Manzana"), rows.map { it.entry.name })
    }

    @Test
    fun `a query that is not an ingredient offers to add it as typed`() {
        val content = pantryPickerContent(ingredients, emptySet(), "  Papel de cocina ")

        assertTrue(content.groups.isEmpty())
        assertEquals(PantryPickRow(PantryEntry("Papel de cocina", null), "cocina-papel", false), content.freeText)
    }

    @Test
    fun `no free row for an ingredient in plural, something already there, an empty key or a long name`() {
        assertNull(pantryPickerContent(ingredients, emptySet(), "tomates").freeText)
        assertNull(pantryPickerContent(ingredients, setOf("cocina-papel"), "papel de cocina").freeText)
        assertNull(pantryPickerContent(ingredients, emptySet(), "¡¿!?").freeText)
        assertNull(pantryPickerContent(ingredients, emptySet(), "a".repeat(121)).freeText)
    }
}
