package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.Nutrition
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientBrandFirestoreTest {
    private fun input(
        name: String = "Hacendado",
        barcode: String? = null,
        stores: List<Supermarket> = emptyList(),
        nutrition: Nutrition? = null,
        favorite: Boolean = false,
    ) = BrandInput("tomate", name, barcode, nutrition, favorite, stores)

    private fun failureOf(block: () -> Unit): BrandFailure? = try {
        block()
        null
    } catch (error: BrandRepositoryException) {
        error.failure
    }

    @Test
    fun `normalizeStores drops INDIFERENTE and duplicates keeping order and caps at six`() {
        val stores = listOf(
            Supermarket.DIA,
            Supermarket.INDIFERENTE,
            Supermarket.EROSKI,
            Supermarket.DIA,
            Supermarket.MERCADONA,
            Supermarket.CARREFOUR,
            Supermarket.ALCAMPO,
            Supermarket.OTROS,
        )

        assertEquals(
            listOf(
                Supermarket.DIA,
                Supermarket.EROSKI,
                Supermarket.MERCADONA,
                Supermarket.CARREFOUR,
                Supermarket.ALCAMPO,
                Supermarket.OTROS,
            ),
            normalizeStores(stores),
        )
        assertEquals(emptyList<Supermarket>(), normalizeStores(listOf(Supermarket.INDIFERENTE)))
    }

    @Test
    fun `validation trims, nulls blank barcode and enforces limits`() {
        val ok = validateBrandInput(
            input(name = "  Hacendado ", barcode = "   ", stores = listOf(Supermarket.DIA, Supermarket.DIA)),
        )
        assertEquals("Hacendado", ok.name)
        assertNull(ok.barcode)
        assertEquals(listOf(Supermarket.DIA), ok.stores)
        assertEquals("841", validateBrandInput(input(barcode = " 841 ")).barcode)

        assertEquals(BrandFailure.NameRequired, failureOf { validateBrandInput(input(name = "  ")) })
        assertEquals(BrandFailure.NameTooLong, failureOf { validateBrandInput(input(name = "a".repeat(61))) })
        assertNull(failureOf { validateBrandInput(input(name = "a".repeat(60))) })
        assertEquals(BrandFailure.BarcodeTooLong, failureOf { validateBrandInput(input(barcode = "1".repeat(33))) })
        assertNull(failureOf { validateBrandInput(input(barcode = "1".repeat(32))) })
    }

    @Test
    fun `firestore fields carry favorite, store names and only present nutrients`() {
        val fields = input(
            barcode = "841",
            favorite = true,
            stores = listOf(Supermarket.DIA, Supermarket.OTROS),
            nutrition = Nutrition(energyKcal = 30.0, salt = 0.1),
        ).toFirestoreFields()

        assertEquals("tomate", fields["ingredientId"])
        assertEquals("Hacendado", fields["name"])
        assertEquals(true, fields["favorite"])
        assertEquals(listOf("DIA", "OTROS"), fields["stores"])
        assertEquals("841", fields["barcode"])
        assertEquals(30.0, fields["energyKcal"])
        assertEquals(0.1, fields["salt"])
        assertFalse(fields.containsKey("fat"))

        val bare = input().toFirestoreFields()
        assertFalse(bare.containsKey("barcode"))
        assertFalse(bare.containsKey("energyKcal"))
        assertEquals(emptyList<String>(), bare["stores"])
    }

    @Test
    fun `parse reads favorite and stores and skips unknown stores`() {
        val brand = parseIngredientBrand(
            "b1",
            mapOf(
                "ingredientId" to "tomate",
                "name" to "Hacendado",
                "favorite" to true,
                "stores" to listOf("DIA", "PLUTO", "EROSKI", 3),
                "barcode" to "841",
                "energyKcal" to 30.0,
            ),
        )

        assertEquals(
            IngredientBrand(
                id = "b1",
                ingredientId = "tomate",
                name = "Hacendado",
                barcode = "841",
                nutrition = Nutrition(energyKcal = 30.0),
                favorite = true,
                stores = listOf(Supermarket.DIA, Supermarket.EROSKI),
            ),
            brand,
        )
        val plain = parseIngredientBrand("b2", mapOf("ingredientId" to "x", "name" to "y", "favorite" to "yes"))
        assertEquals(false, plain?.favorite)
        assertNull(plain?.nutrition)
        assertEquals(emptyList<Supermarket>(), plain?.stores)
    }

    @Test
    fun `parse drops a brand without ingredientId or name`() {
        assertNull(parseIngredientBrand("b", mapOf("name" to "Hacendado")))
        assertNull(parseIngredientBrand("b", mapOf("ingredientId" to "tomate")))
        assertNull(parseIngredientBrand("b", mapOf("ingredientId" to "tomate", "name" to "  ")))
    }

    @Test
    fun `brands sort favorites first then by name ignoring accents`() {
        fun brand(id: String, name: String, favorite: Boolean = false) =
            IngredientBrand(id = id, ingredientId = "x", name = name, favorite = favorite)

        val sorted = listOf(
            brand("1", "Zeta"),
            brand("2", "Ágata"),
            brand("3", "beta"),
            brand("4", "Omega", favorite = true),
            brand("5", "Alfa", favorite = true),
            brand("0", "beta"),
        ).sortedForBrands()

        assertEquals(listOf("5", "4", "2", "0", "3", "1"), sorted.map(IngredientBrand::id))
        assertTrue(sorted.first().favorite)
    }

    @Test
    fun `update fields leave out the ingredient and delete the barcode and nutrients that are missing`() {
        val fields = input(barcode = null, nutrition = Nutrition(fat = 1.2), stores = listOf(Supermarket.DIA))
            .toUpdateFields()

        assertFalse(fields.containsKey("ingredientId"))
        assertEquals(FieldValue.delete(), fields["barcode"])
        assertEquals(1.2, fields["fat"])
        for (key in NUTRITION_FIELDS - "fat") assertEquals(key, FieldValue.delete(), fields[key])
        assertEquals("Hacendado", fields["name"])
        assertEquals(listOf(Supermarket.DIA.name), fields["stores"])
    }

    @Test
    fun `update fields with every value present delete nothing`() {
        val nutrition = Nutrition(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
        val fields = input(barcode = "123", nutrition = nutrition).toUpdateFields()

        assertTrue(fields.values.none { it == FieldValue.delete() })
        assertEquals("123", fields["barcode"])
    }
}
