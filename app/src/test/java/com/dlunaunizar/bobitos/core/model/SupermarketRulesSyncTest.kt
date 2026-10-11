package com.dlunaunizar.bobitos.core.model

import com.dlunaunizar.bobitos.feature.shopping.brandColor
import com.dlunaunizar.bobitos.feature.shopping.icon
import com.dlunaunizar.bobitos.feature.shopping.labelRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SupermarketRulesSyncTest {
    private val rules: String =
        listOf(File("../firestore.rules"), File("firestore.rules")).first(File::exists).readText()

    private fun listAfter(marker: String): List<String> {
        val start = rules.indexOf(marker)
        assertTrue("no se encuentra $marker en firestore.rules", start >= 0)
        val open = rules.indexOf('[', start)
        val close = rules.indexOf(']', open)
        return rules.substring(open + 1, close).split(',').map { it.trim().trim('"') }.filter { it.isNotEmpty() }
    }

    @Test
    fun `the shopping item rule lists exactly the Supermarket values`() {
        assertEquals(Supermarket.entries.map { it.name }, listAfter("request.resource.data.supermarket in"))
    }

    @Test
    fun `the brand store rule lists every value but INDIFERENTE`() {
        assertEquals(
            Supermarket.entries.filter { it != Supermarket.INDIFERENTE }.map { it.name },
            listAfter("stores.hasOnly"),
        )
    }

    @Test
    fun `the Supermarket order is supermarkets, local shops, others and indifferent`() {
        assertEquals(
            listOf(
                "DIA", "EROSKI", "MERCADONA", "CARREFOUR", "ALCAMPO",
                "CARNICERIA", "PESCADERIA", "FRUTERIA", "PANADERIA", "OTROS", "INDIFERENTE",
            ),
            Supermarket.entries.map { it.name },
        )
    }

    @Test
    fun `every Supermarket has a label, a color and an icon`() {
        Supermarket.entries.forEach {
            assertTrue(it.labelRes != 0)
            assertNotNull(it.brandColor())
            assertNotNull(it.icon())
        }
    }
}
