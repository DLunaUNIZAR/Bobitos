package com.dlunaunizar.bobitos.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingAndRecentTest {
    private data class Item(val id: String, val done: Boolean, val order: Int)

    private fun merge(pending: List<Item>, recent: List<Item>, pendingCap: Int = 3, doneLimit: Int = 2) =
        mergePendingAndRecent(
            pending = pending,
            recent = recent,
            pendingCap = pendingCap,
            doneLimit = doneLimit,
            id = Item::id,
            isDone = Item::done,
            order = compareBy(Item::order).thenBy(Item::id),
        )

    @Test
    fun `joins pending and done in the given order without flags when everything fits`() {
        val result = merge(
            pending = listOf(Item("b", false, 2), Item("a", false, 1)),
            recent = listOf(Item("c", true, 0)),
        )

        assertEquals(listOf("c", "a", "b"), result.items.map(Item::id))
        assertFalse(result.hasMoreDone)
        assertFalse(result.pendingCapReached)
    }

    @Test
    fun `the extra done item read past the limit means there are more and is not shown`() {
        val result = merge(
            pending = emptyList(),
            recent = listOf(Item("d1", true, 1), Item("d2", true, 2), Item("d3", true, 3)),
        )

        assertEquals(listOf("d1", "d2"), result.items.map(Item::id))
        assertTrue(result.hasMoreDone)
    }

    @Test
    fun `pending items at the tail of the done query are dropped and do not count as more`() {
        // La consulta de hechas, sin filtro, trae al final las pendientes (fecha de hecha a null).
        val result = merge(
            pending = listOf(Item("p1", false, 5)),
            recent = listOf(Item("d1", true, 1), Item("d2", true, 2), Item("p1", false, 5)),
        )

        assertEquals(listOf("d1", "d2", "p1"), result.items.map(Item::id))
        assertFalse(result.hasMoreDone)
    }

    @Test
    fun `an item in both queries while it changes state shows once, as the pending one`() {
        val result = merge(
            pending = listOf(Item("x", false, 1)),
            recent = listOf(Item("x", true, 1), Item("d", true, 2)),
        )

        assertEquals(listOf(Item("x", false, 1), Item("d", true, 2)), result.items)
    }

    @Test
    fun `an item that just moved from pending to done keeps its place`() {
        val before = merge(
            pending = listOf(Item("a", false, 1), Item("b", false, 2)),
            recent = emptyList(),
        )
        val after = merge(
            pending = listOf(Item("b", false, 2)),
            recent = listOf(Item("a", true, 1)),
        )

        assertEquals(before.items.map(Item::id), after.items.map(Item::id))
    }

    @Test
    fun `more pending than the cap are cut and flagged`() {
        val result = merge(
            pending = (1..4).map { Item("p$it", false, it) },
            recent = emptyList(),
        )

        assertEquals(3, result.items.size)
        assertTrue(result.pendingCapReached)
    }

    @Test
    fun `exactly the cap of pending is not flagged`() {
        val result = merge(pending = (1..3).map { Item("p$it", false, it) }, recent = emptyList())

        assertEquals(3, result.items.size)
        assertFalse(result.pendingCapReached)
    }

    @Test
    fun `ties in the order are broken by id so the list does not jump`() {
        val first = merge(
            pending = listOf(Item("b", false, 1), Item("a", false, 1)),
            recent = listOf(Item("d", true, 1), Item("c", true, 1)),
        )
        val second = merge(
            pending = listOf(Item("a", false, 1), Item("b", false, 1)),
            recent = listOf(Item("c", true, 1), Item("d", true, 1)),
        )

        assertEquals(listOf("a", "b", "c", "d"), first.items.map(Item::id))
        assertEquals(first.items, second.items)
    }
}
