package com.dlunaunizar.bobitos.data.openfoodfacts

import com.dlunaunizar.bobitos.core.model.Nutrition
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsClientTest {
    @Test
    fun `parses a full product taking the first brand`() {
        val product = parseOffProduct(
            """
            {
              "status": 1,
              "product": {
                "product_name": "Tomate frito",
                "brands": "Hacendado, Mercadona",
                "nutriments": {
                  "energy-kcal_100g": 80,
                  "fat_100g": 3.5,
                  "carbohydrates_100g": 9,
                  "sugars_100g": 6,
                  "proteins_100g": 1.5,
                  "salt_100g": 0.9
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("Tomate frito", product?.productName)
        assertEquals("Hacendado", product?.brand)
        assertEquals(Nutrition(80.0, 3.5, 9.0, 6.0, 1.5, 0.9), product?.nutrition)
    }

    @Test
    fun `returns null when the product does not exist`() {
        assertNull(parseOffProduct("""{ "status": 0, "status_verbose": "product not found" }"""))
    }

    @Test
    fun `missing nutriments yield an empty nutrition`() {
        val product = parseOffProduct("""{ "status": 1, "product": { "product_name": "Sal" } }""")

        assertEquals("Sal", product?.productName)
        assertNull(product?.brand)
        assertTrue(product?.nutrition?.isEmpty() == true)
    }

    @Test
    fun `lookup requests the barcode digits and parses the body`() = runTest {
        val http = FakeOffHttpClient(
            body = """{ "status": 1, "product": { "product_name": "Arroz", "brands": "SOS" } }""",
        )
        val client = HttpOpenFoodFactsClient(http)

        val product = client.lookup(" 84100-00 ")

        assertEquals("Arroz", product?.productName)
        assertTrue(http.lastUrl?.contains("/product/8410000.json") == true)
    }

    @Test
    fun `lookup returns null for a blank barcode without hitting the network`() = runTest {
        val http = FakeOffHttpClient(body = null)
        val client = HttpOpenFoodFactsClient(http)

        assertNull(client.lookup("   "))
        assertNull(http.lastUrl)
    }
}

private class FakeOffHttpClient(private val body: String?) : OffHttpClient {
    var lastUrl: String? = null
    override suspend fun get(url: String): String? {
        lastUrl = url
        return body
    }
}
