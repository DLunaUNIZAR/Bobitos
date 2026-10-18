package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.core.designsystem.component.RowPosition
import com.dlunaunizar.bobitos.core.designsystem.component.rowPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogGroupsTest {
    private data class Item(val name: String, val mine: Boolean = false, val category: String? = null)

    private fun group(items: List<Item>, layout: CatalogLayout, selected: String? = null) =
        groupCatalog(items, { it.mine }, { it.category }, layout, selected)

    private fun List<CatalogGroup<Item>>.names() = map { g -> g.items.map { it.name } }

    @Test
    fun porCategoriaVanPrimeroLosTuyosYLuegoCadaCategoriaOrdenada() {
        val items = listOf(
            Item("Pez", category = "Pescados"),
            Item("Ñoqui", category = "Ñoquis"),
            Item("Paella", category = "Arroces"),
            Item("Mi tarta", mine = true, category = "Postres"),
        )
        val groups = group(items, CatalogLayout.BY_CATEGORY)
        assertEquals(
            listOf(
                CatalogHeader.Mine,
                CatalogHeader.Category("Arroces"),
                CatalogHeader.Category("Ñoquis"),
                CatalogHeader.Category("Pescados"),
            ),
            groups.map { it.header },
        )
        assertEquals(listOf(listOf("Mi tarta"), listOf("Paella"), listOf("Ñoqui"), listOf("Pez")), groups.names())
    }

    @Test
    fun sinCategoriaVaAlFinalEnOtras() {
        val items = listOf(
            Item("Suelta"),
            Item("Blanca", category = " "),
            Item("Paella", category = "Arroces"),
            Item("Mía sin categoría", mine = true),
        )
        val groups = group(items, CatalogLayout.BY_CATEGORY)
        assertEquals(
            listOf(CatalogHeader.Mine, CatalogHeader.Category("Arroces"), CatalogHeader.Other),
            groups.map { it.header },
        )
        assertEquals(listOf("Suelta", "Blanca"), groups.last().items.map { it.name })
    }

    @Test
    fun conUnaCategoriaElegidaSoloSaleEseGrupoConLosTuyosPrimero() {
        val items = listOf(
            Item("Paella", category = "Arroces"),
            Item("Mi arroz", mine = true, category = " arroces "),
            Item("Pez", category = "Pescados"),
        )
        val groups = group(items, CatalogLayout.BY_CATEGORY, selected = "Arroces")
        assertEquals(listOf(CatalogHeader.Category("Arroces")), groups.map { it.header })
        assertEquals(listOf("Mi arroz", "Paella"), groups.single().items.map { it.name })
    }

    @Test
    fun planaPoneLosTuyosPrimeroSinCabecera() {
        val items = listOf(Item("B"), Item("A", mine = true), Item("C"), Item("D", mine = true))
        val groups = group(items, CatalogLayout.FLAT)
        assertEquals(1, groups.size)
        assertNull(groups.single().header)
        assertEquals(listOf("A", "D", "B", "C"), groups.single().items.map { it.name })
    }

    @Test
    fun categoriasQueSoloDifierenEnMayusculasSeJuntan() {
        val items = listOf(
            Item("Uno", category = "Arroces"),
            Item("Dos", category = "arroces"),
            Item("Tres", category = "Pastas"),
        )
        val groups = group(items, CatalogLayout.BY_CATEGORY)
        assertEquals(2, groups.size)
        assertEquals(listOf("Uno", "Dos"), groups.first().items.map { it.name })
    }

    @Test
    fun nuncaHayGruposVacios() {
        assertTrue(group(emptyList(), CatalogLayout.BY_CATEGORY).isEmpty())
        assertTrue(group(emptyList(), CatalogLayout.FLAT).isEmpty())
        assertTrue(group(listOf(Item("A")), CatalogLayout.BY_CATEGORY, selected = "Nada").isEmpty())
        val soloComunes = group(listOf(Item("A", category = "X")), CatalogLayout.BY_CATEGORY)
        assertTrue(soloComunes.none { it.items.isEmpty() })
        assertEquals(listOf(CatalogHeader.Category("X")), soloComunes.map { it.header })
    }

    @Test
    fun rowPositionDaLosExtremos() {
        assertEquals(RowPosition.SINGLE, rowPosition(0, 1))
        assertEquals(RowPosition.FIRST, rowPosition(0, 3))
        assertEquals(RowPosition.MIDDLE, rowPosition(1, 3))
        assertEquals(RowPosition.LAST, rowPosition(2, 3))
    }
}
