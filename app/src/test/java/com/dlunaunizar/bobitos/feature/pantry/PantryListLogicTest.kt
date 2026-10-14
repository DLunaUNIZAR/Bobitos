package com.dlunaunizar.bobitos.feature.pantry

import com.dlunaunizar.bobitos.core.model.PantryItem
import com.dlunaunizar.bobitos.core.model.pantryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryListLogicTest {
    private fun item(name: String, category: String? = null) = PantryItem(pantryId(name), name, category, null)

    @Test
    fun `groups follow the catalog order and others go last`() {
        val sections = pantrySections(
            listOf(
                item("Sal", "Especias y hierbas"),
                item("Papel de cocina"),
                item("Manzana", "Frutas"),
                item("Caprichos raros", "Caprichos"),
                item("Pera", "Frutas"),
            ),
            "",
        )

        assertEquals(listOf("Frutas", "Especias y hierbas", null), sections.map { it.category })
        assertEquals(listOf("Manzana", "Pera"), sections[0].items.map { it.name })
        assertEquals(listOf("Caprichos raros", "Papel de cocina"), sections[2].items.map { it.name })
    }

    @Test
    fun `names sort alphabetically ignoring case and accents`() {
        val names = pantrySections(
            listOf(
                item("Orégano", "Especias y hierbas"),
                item("comino", "Especias y hierbas"),
                item("Ajo en polvo", "Especias y hierbas"),
            ),
            "",
        ).single().items.map { it.name }

        assertEquals(listOf("Ajo en polvo", "comino", "Orégano"), names)
    }

    @Test
    fun `search matches the name or the category without accents`() {
        val items =
            listOf(item("Limón", "Frutas"), item("Sal", "Especias y hierbas"), item("Salsa casera", "Caprichos"))

        assertEquals(listOf("Limón"), pantrySections(items, "limon").flatMap { it.items }.map { it.name })
        assertEquals(listOf("Sal"), pantrySections(items, "especias").flatMap { it.items }.map { it.name })
        assertEquals(listOf("Salsa casera"), pantrySections(items, "caprichos").flatMap { it.items }.map { it.name })
        assertTrue(pantrySections(items, "pescado").isEmpty())
    }
}
