package com.dlunaunizar.bobitos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private val RULES_CANDIDATES = listOf(File("../firestore.rules"), File("firestore.rules"))

class SupermarketRulesSyncTest {
    private val rules: String =
        RULES_CANDIDATES.firstOrNull(File::exists)
            .let { checkNotNull(it) { "no se encuentra firestore.rules; rutas probadas: $RULES_CANDIDATES" } }
            .readText()

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
    fun `the store list limit in the rules matches MAX_STORES`() {
        val limit = Regex("""stores\.size\(\) <= (\d+)""").find(rules)?.groupValues?.get(1)?.toInt()
        assertEquals(MAX_STORES, limit)
    }
}
