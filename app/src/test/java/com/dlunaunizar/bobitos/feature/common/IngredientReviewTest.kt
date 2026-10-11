package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class IngredientReviewTest {
    private val arroz = CatalogIngredient("arroz", "Arroz")
    private val data = IngredientShoppingData(
        ingredients = listOf(arroz),
        brands = listOf(
            IngredientBrand("b1", "arroz", "Brillante", favorite = true, stores = listOf(Supermarket.DIA)),
            IngredientBrand("b2", "arroz", "Sos"),
        ),
        prefs = mapOf("arroz" to IngredientPref(listOf(Supermarket.MERCADONA))),
    )

    @Test
    fun `a new row matching an ingredient by key gets its context, an existing row does not`() {
        val rows = buildIngredientReviewRows(
            listOf(Ingredient("ARROZ", "300", "g")),
            emptyList(),
            data,
        )
        assertNotNull(rows.single().context)
        assertEquals("arroz", rows.single().context?.ingredient?.id)

        val existing = buildIngredientReviewRows(
            listOf(Ingredient("Arroz", "300", "g")),
            listOf(item("s1", "arroz")),
            data,
        )
        assertNull(existing.single().context)
    }

    @Test
    fun `a new row without ingredient has no context and adds without store or brand`() = runTest {
        val rows = buildIngredientReviewRows(listOf(Ingredient("Sal", "1", "kg")), emptyList(), data)
        val row = rows.single()
        assertNull(row.context)

        val choice = row.initialState().toChoice(row)
        assertEquals(IngredientReviewChoice("1", null, null), choice)

        val shopping = RecordingShoppingRepository()
        applyIngredientReview("home", rows, listOf(choice), shopping)
        assertEquals(listOf("Sal"), shopping.addedNames)
        assertEquals(listOf<Supermarket?>(null), shopping.addedSupermarkets)
        assertEquals(listOf<String?>(null), shopping.addedBrands)
    }

    @Test
    fun `the initial state uses the single favorite and its first store`() {
        val row = buildIngredientReviewRows(listOf(Ingredient("Arroz", "300", "g")), emptyList(), data).single()
        val state = row.initialState()

        assertEquals("300", state.quantity)
        assertEquals(BrandStoreSelection("b1", Supermarket.DIA), state.selection)
        assertEquals("", state.freeBrand)
    }

    @Test
    fun `choices carry the picked brand name, indifferent as null, and a free brand when there are no brands`() {
        val row = buildIngredientReviewRows(listOf(Ingredient("Arroz", "300", "g")), emptyList(), data).single()
        val picked = row.initialState().copy(quantity = " 500 ")
        assertEquals(
            IngredientReviewChoice("500", Supermarket.DIA, "Brillante"),
            picked.toChoice(row),
        )
        val indifferent = picked.copy(selection = BrandStoreSelection(null, Supermarket.CARREFOUR))
        assertEquals(IngredientReviewChoice("500", Supermarket.CARREFOUR, null), indifferent.toChoice(row))

        val noBrands = data.copy(brands = emptyList())
        val freeRow = buildIngredientReviewRows(listOf(Ingredient("Arroz", "300", "g")), emptyList(), noBrands)
            .single()
        val free = freeRow.initialState().copy(quantity = "", freeBrand = "  Hacendado ")
        assertEquals(IngredientReviewChoice(null, Supermarket.MERCADONA, "Hacendado"), free.toChoice(freeRow))
    }

    @Test
    fun `apply keeps brand and store of rows already in the list and adds new rows with their choice`() = runTest {
        val existing = item("s1", "Sal", supermarket = Supermarket.EROSKI, brand = "Costa")
        val rows = buildIngredientReviewRows(
            listOf(Ingredient("Sal", "1", "kg"), Ingredient("Arroz", "300", "g")),
            listOf(existing),
            data,
        )
        val choices = listOf(
            IngredientReviewChoice("2", null, null),
            IngredientReviewChoice("300", Supermarket.DIA, "Brillante"),
        )
        val shopping = RecordingShoppingRepository()

        applyIngredientReview("home", rows, choices, shopping)

        assertEquals(listOf("Arroz"), shopping.addedNames)
        assertEquals(listOf("g"), shopping.addedNotes)
        assertEquals(listOf<Supermarket?>(Supermarket.DIA), shopping.addedSupermarkets)
        assertEquals(listOf<String?>("Brillante"), shopping.addedBrands)
        assertEquals(listOf("s1"), shopping.updatedIds)
        assertEquals(listOf<String?>("2"), shopping.updatedQuantities)
        assertEquals(listOf<Supermarket?>(Supermarket.EROSKI), shopping.updatedSupermarkets)
        assertEquals(listOf<String?>("Costa"), shopping.updatedBrands)
    }

    private fun item(id: String, name: String, supermarket: Supermarket? = null, brand: String? = null) = ShoppingItem(
        id = id,
        name = name,
        quantity = null,
        notes = null,
        purchased = false,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
        purchasedBy = null,
        purchasedByName = null,
        purchasedAt = null,
        supermarket = supermarket,
        brand = brand,
    )
}
