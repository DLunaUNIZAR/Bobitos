package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.pantryId
import com.dlunaunizar.bobitos.data.repository.ShoppingWrite
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class IngredientReviewTest {
    @Test
    fun `consecutive brand and store changes on a row are both kept`() {
        val context = IngredientShoppingContext(arroz, data.brands, listOf(Supermarket.MERCADONA, Supermarket.DIA))
        val start = ReviewRowState("1", BrandStoreSelection.None, "")

        val result = start.withBrand(context, "b2").withStore(Supermarket.MERCADONA)

        assertEquals("b2", result.selection.brandId)
        assertEquals(Supermarket.MERCADONA, result.selection.store)
        assertEquals("7", start.copy(quantity = "7").withFreeBrand("X").quantity)
        assertEquals("X", start.withFreeBrand("X").freeBrand)
    }

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
        assertEquals(IngredientReviewChoice("1 kg", null, null), choice)

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

        assertEquals("300 g", state.quantity)
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
        assertEquals(listOf<String?>(null), shopping.addedNotes)
        assertEquals(listOf<String?>("300"), shopping.addedQuantities)
        assertEquals(listOf<Supermarket?>(Supermarket.DIA), shopping.addedSupermarkets)
        assertEquals(listOf<String?>("Brillante"), shopping.addedBrands)
        assertEquals(listOf("s1"), shopping.updatedIds)
        assertEquals(listOf<String?>("2"), shopping.updatedQuantities)
        assertEquals(listOf<Supermarket?>(Supermarket.EROSKI), shopping.updatedSupermarkets)
        assertEquals(listOf<String?>("Costa"), shopping.updatedBrands)
    }

    @Test
    fun `a pending item is omitted by default with the sum ready in case of Sumar`() = runTest {
        val rows = buildIngredientReviewRows(
            listOf(Ingredient("Arroz", "300", "g")),
            listOf(item("s1", "arroz", quantity = "200 g")),
            data,
        )
        val row = rows.single()
        val state = row.initialState()

        assertEquals(ReviewRowKind.PENDING, row.kind)
        assertFalse(state.include)
        assertEquals("500 g", state.quantity)
        val choices = listOf(state.toChoice(row))
        assertTrue(choices.writesNothing())

        val shopping = RecordingShoppingRepository()
        applyIngredientReview("home", rows, choices, shopping)
        assertTrue(shopping.updatedIds.isEmpty())
        assertTrue(shopping.addedNames.isEmpty())
        assertTrue(shopping.relistedIds.isEmpty())
    }

    @Test
    fun `choosing Sumar updates only the quantity of the pending item`() = runTest {
        val existing = item("s1", "Arroz", quantity = "200 g", supermarket = Supermarket.DIA, brand = "Sos")
        val rows = buildIngredientReviewRows(listOf(Ingredient("Arroz", "300", "g")), listOf(existing), data)
        val row = rows.single()
        val choice = row.initialState().withInclude(true).toChoice(row)

        assertFalse(choice.skip)
        val shopping = RecordingShoppingRepository()
        applyIngredientReview("home", rows, listOf(choice), shopping)

        assertEquals(listOf("s1"), shopping.updatedIds)
        assertEquals(listOf<String?>("500 g"), shopping.updatedQuantities)
        assertEquals(listOf<Supermarket?>(Supermarket.DIA), shopping.updatedSupermarkets)
        assertEquals(listOf<String?>("Sos"), shopping.updatedBrands)
        assertTrue(shopping.addedNames.isEmpty())
    }

    @Test
    fun `a purchased item is omitted by default and relisted only when chosen`() {
        val bought = item("s2", "Leche", quantity = "6 l", purchased = true)
        val row = buildIngredientReviewRows(listOf(Ingredient("Leche", "1", "l")), listOf(bought), data).single()
        val state = row.initialState()

        assertFalse(state.include)
        assertEquals("1 l", state.quantity)
        assertTrue(listOf(state.toChoice(row)).writesNothing())
        assertEquals(
            listOf(ShoppingWrite.Change(bought, "1 l", null, null, null, relist = true)),
            ingredientReviewWrites(listOf(row), listOf(state.withInclude(true).toChoice(row))),
        )
    }

    @Test
    fun `every row shows the pantry and the list badges it has, pantry first`() {
        val rows = buildIngredientReviewRows(
            listOf(
                Ingredient("Sal", "200", "g"),
                Ingredient("Leche", "1", "l"),
                Ingredient("Arroz"),
                Ingredient("Pimienta"),
                Ingredient("Tomate"),
            ),
            listOf(
                item("s1", "Sal", quantity = "1 kg"),
                item("s2", "Leche", purchased = true),
                item("s3", "Pimienta"),
            ),
            data,
            setOf(pantryId("Sal"), pantryId("Leche"), pantryId("Arroz")),
        )
        val pantry = ReviewBadge(ReviewBadgeSource.PANTRY, R.string.meals_review_badge_pantry)
        val kg = listOf("1 kg")

        assertEquals(
            listOf(
                listOf(pantry, ReviewBadge(ReviewBadgeSource.SHOPPING, R.string.meals_review_badge_listed, kg)),
                listOf(pantry, ReviewBadge(ReviewBadgeSource.SHOPPING, R.string.meals_review_badge_purchased)),
                listOf(pantry),
                listOf(ReviewBadge(ReviewBadgeSource.SHOPPING, R.string.meals_review_badge_listed_plain)),
                emptyList(),
            ),
            rows.map { it.badges() },
        )
    }

    @Test
    fun `the hint only carries what the recipe asks for`() {
        val salRow = buildIngredientReviewRows(
            listOf(Ingredient("Sal", "200", "g")),
            listOf(item("s1", "Sal", quantity = "1 kg")),
            data,
            setOf(pantryId("Sal")),
        ).single()
        assertEquals(ReviewHint(R.string.meals_review_recipe, listOf("200 g")), salRow.hint())

        val bought = listOf(item("s2", "Leche", purchased = true))
        assertNull(buildIngredientReviewRows(listOf(Ingredient("Leche")), bought, data).single().hint())

        assertEquals(
            ReviewHint(R.string.meals_review_recipe, listOf("1 kg")),
            buildIngredientReviewRows(listOf(Ingredient("Sal", "1", "kg")), emptyList(), data, setOf(pantryId("Sal")))
                .single().hint(),
        )

        val pair = listOf(Ingredient("Sal", "200", "g"), Ingredient("Sal", "1", "ud"))
        val multi = ReviewHint(R.string.meals_review_multi, listOf("200 g + 1 ud"))
        assertEquals(multi, buildIngredientReviewRows(pair, listOf(item("s1", "Sal")), data).single().hint())
        assertEquals(multi, buildIngredientReviewRows(pair, emptyList(), data).single().hint())
    }

    @Test
    fun `rows without any quantity write no quantity when summed or relisted`() {
        val pending = item("s1", "Sal")
        val bought = item("s2", "Pimienta", purchased = true)
        val rows =
            buildIngredientReviewRows(listOf(Ingredient("Sal"), Ingredient("Pimienta")), listOf(pending, bought), data)

        assertEquals(
            listOf(
                ShoppingWrite.Change(pending, null, null, null, null, relist = false),
                ShoppingWrite.Change(bought, null, null, null, null, relist = true),
            ),
            ingredientReviewWrites(rows, rows.map { it.initialState().withInclude(true).toChoice(it) }),
        )
    }

    @Test
    fun `a purchased item is relisted with the recipe quantity keeping its brand and store`() = runTest {
        val bought = item(
            "s2",
            "Leche",
            quantity = "6 l",
            supermarket = Supermarket.DIA,
            brand = "Pascual",
            purchased = true,
        )
        val rows = buildIngredientReviewRows(listOf(Ingredient("Leche", "1", "l")), listOf(bought), data)
        val row = rows.single()
        val state = row.initialState()

        assertEquals(ReviewRowKind.PURCHASED, row.kind)
        assertFalse(state.include)
        assertEquals("1 l", state.quantity)
        assertTrue(listOf(state.toChoice(row)).writesNothing())

        val shopping = RecordingShoppingRepository()
        applyIngredientReview("home", rows, listOf(state.withInclude(true).toChoice(row)), shopping)
        assertEquals(listOf("s2"), shopping.relistedIds)
        assertEquals(listOf<String?>("1 l"), shopping.relistedQuantities)
        assertEquals(listOf<Supermarket?>(Supermarket.DIA), shopping.relistedSupermarkets)
        assertEquals(listOf<String?>("Pascual"), shopping.relistedBrands)
        assertTrue(shopping.addedNames.isEmpty())
        assertTrue(shopping.updatedIds.isEmpty())
    }

    @Test
    fun `singular and plural spellings share one row with the quantities added`() {
        val rows = buildIngredientReviewRows(
            listOf(Ingredient("Tomate", "2", "ud"), Ingredient("tomates", "3", "ud")),
            emptyList(),
            data,
        )

        assertEquals(1, rows.size)
        assertEquals("Tomate", rows.single().name)
        assertEquals("5 ud", rows.single().recipeQuantity)
        assertEquals(listOf("2 ud", "3 ud"), rows.single().quantities)
    }

    @Test
    fun `contributions in different units are grouped by unit when folded`() {
        val rows = buildIngredientReviewRows(
            listOf(Ingredient("Tomate", "200", "g"), Ingredient("Tomate", "1", "ud"), Ingredient("Tomate", "300", "g")),
            emptyList(),
            data,
        )

        assertEquals("500 g + 1 ud", rows.single().recipeQuantity)
    }

    @Test
    fun `a pending item wins over a purchased one with the same name`() {
        val rows = buildIngredientReviewRows(
            listOf(Ingredient("Tomate", "2", "ud")),
            listOf(item("s1", "Tomates", purchased = true), item("s2", "tomate")),
            data,
        )

        assertEquals("s2", rows.single().existing?.id)
        assertEquals(ReviewRowKind.PENDING, rows.single().kind)
    }

    @Test
    fun `an ingredient without quantity keeps the pending quantity when summing`() {
        val row = buildIngredientReviewRows(
            listOf(Ingredient("Sal")),
            listOf(item("s1", "Sal", quantity = "1 kg")),
            data,
        ).single()

        assertEquals("1 kg", row.initialState().quantity)
    }

    @Test
    fun `a quantity over 40 characters blocks confirming only when it will be written`() {
        val long = ReviewRowState("x".repeat(41), BrandStoreSelection.None, "")

        assertTrue(long.quantityTooLong())
        assertFalse(long.withInclude(false).quantityTooLong())
        assertFalse(long.copy(quantity = "x".repeat(40)).quantityTooLong())
    }

    @Test
    fun `new rows are added with the unit inside the quantity and without notes`() = runTest {
        val rows = buildIngredientReviewRows(listOf(Ingredient("Sal", "1", "kg")), emptyList(), data)
        val row = rows.single()
        val shopping = RecordingShoppingRepository()

        applyIngredientReview("home", rows, listOf(row.initialState().toChoice(row)), shopping)

        assertEquals(listOf<String?>("1 kg"), shopping.addedQuantities)
        assertEquals(listOf<String?>(null), shopping.addedNotes)
    }

    private fun item(
        id: String,
        name: String,
        supermarket: Supermarket? = null,
        brand: String? = null,
        quantity: String? = null,
        purchased: Boolean = false,
    ) = ShoppingItem(
        id = id,
        name = name,
        quantity = quantity,
        notes = null,
        purchased = purchased,
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

    @Test
    fun `the whole review is written in one batch in row order`() = runTest {
        val pending = item("s1", "Arroz", supermarket = Supermarket.DIA, brand = "Sos", quantity = "200 g")
        val bought = item("s2", "Leche", quantity = "6 l", purchased = true)
        val rows = buildIngredientReviewRows(
            listOf(Ingredient("Sal", "1", "kg"), Ingredient("Arroz", "300", "g"), Ingredient("Leche", "1", "l")),
            listOf(pending, bought),
            data,
        )
        val choices = rows.map { it.initialState().withInclude(true).toChoice(it) }
        val shopping = RecordingShoppingRepository()

        applyIngredientReview("home", rows, choices, shopping)

        assertEquals(
            listOf(
                listOf(
                    ShoppingWrite.Add("Sal", "1 kg", null, null, null),
                    ShoppingWrite.Change(pending, "500 g", null, Supermarket.DIA, "Sos", relist = false),
                    ShoppingWrite.Change(bought, "1 l", null, null, null, relist = true),
                ),
            ),
            shopping.appliedBatches,
        )
    }

    @Test
    fun `a review with every row omitted writes no batch`() = runTest {
        val rows = buildIngredientReviewRows(
            listOf(Ingredient("Arroz", "300", "g")),
            listOf(item("s1", "arroz", quantity = "200 g")),
            data,
        )
        val shopping = RecordingShoppingRepository()

        applyIngredientReview("home", rows, rows.map { it.initialState().toChoice(it) }, shopping)

        assertTrue(shopping.appliedBatches.isEmpty())
    }

    @Test
    fun `a free brand longer than the limit marks its row and blocks confirming`() {
        val free = IngredientReviewRow(
            name = "Arroz",
            recipeQuantity = "300 g",
            existing = null,
            context = IngredientShoppingContext(arroz, emptyList(), emptyList()),
        )
        val long = free.initialState().withFreeBrand("x".repeat(61))
        val fits = free.initialState().withFreeBrand("  " + "x".repeat(60) + "  ")

        assertTrue(long.brandTooLong(free))
        assertFalse(fits.brandTooLong(free))
        assertFalse(canConfirmReview(listOf(free), listOf(long)))
        assertTrue(canConfirmReview(listOf(free), listOf(fits)))

        // Con marcas registradas no hay campo libre: su texto no cuenta.
        val withBrands = free.copy(context = IngredientShoppingContext(arroz, data.brands, emptyList()))
        assertFalse(withBrands.initialState().withFreeBrand("x".repeat(61)).brandTooLong(withBrands))
        assertTrue(freeBrandTooLong("x".repeat(61)))
        assertFalse(freeBrandTooLong(" " + "x".repeat(60) + " "))
    }

    @Test
    fun `a quantity that does not fit still blocks confirming`() {
        val row = IngredientReviewRow("Sal", "1 pizca", existing = null)
        val long = row.initialState().copy(quantity = "x".repeat(41))

        assertFalse(canConfirmReview(listOf(row), listOf(long)))
        assertTrue(canConfirmReview(listOf(row), listOf(row.initialState())))
    }

    @Test
    fun `a row in the pantry is omitted by default and added like a new one when chosen`() {
        val row = buildIngredientReviewRows(
            listOf(Ingredient("Arroz", "300", "g")),
            emptyList(),
            data,
            setOf(pantryId("Arroz")),
        ).single()
        val state = row.initialState()

        assertEquals(ReviewRowKind.IN_PANTRY, row.kind)
        assertNotNull(row.context)
        assertFalse(state.include)
        assertTrue(listOf(state.toChoice(row)).writesNothing())
        val added = state.withInclude(true).toChoice(row)
        assertEquals(IngredientReviewChoice("300 g", Supermarket.DIA, "Brillante"), added)
        assertEquals(
            listOf(ShoppingWrite.Add("Arroz", "300 g", null, Supermarket.DIA, "Brillante")),
            ingredientReviewWrites(listOf(row), listOf(added)),
        )
    }

    @Test
    fun `pending wins over the pantry and the pantry over purchased`() {
        val rows = buildIngredientReviewRows(
            listOf(Ingredient("Sal"), Ingredient("Leche", "1", "l"), Ingredient("Arroz")),
            listOf(item("s1", "sal"), item("s2", "leche", purchased = true)),
            data,
            setOf(pantryId("Sal"), pantryId("Leche")),
        )

        assertEquals(listOf(ReviewRowKind.PENDING, ReviewRowKind.IN_PANTRY, ReviewRowKind.NEW), rows.map { it.kind })
        val milk = rows[1]
        assertEquals(R.string.meals_review_relist, milk.includeLabel())
        assertEquals(
            listOf(ShoppingWrite.Change(milk.existing!!, "1 l", null, null, null, relist = true)),
            ingredientReviewWrites(listOf(milk), listOf(milk.initialState().withInclude(true).toChoice(milk))),
        )
    }

    @Test
    fun `a row in the pantry starts in No añadir, also if it was bought`() {
        val rows = buildIngredientReviewRows(
            listOf(Ingredient("Sal"), Ingredient("Leche", "1", "l")),
            listOf(item("s2", "leche", purchased = true)),
            data,
            setOf(pantryId("Sal"), pantryId("Leche")),
        )

        val (salt, milk) = rows
        assertFalse(salt.initialState().include)
        assertEquals(R.string.meals_review_add, salt.includeLabel())
        assertFalse(milk.initialState().include)
        assertEquals(R.string.meals_review_relist, milk.includeLabel())
    }

    @Test
    fun `a new row starts in Añadir and can be left out`() {
        val row = buildIngredientReviewRows(listOf(Ingredient("Arroz", "300", "g")), emptyList(), data).single()
        val state = row.initialState()

        assertTrue(state.include)
        assertEquals(R.string.meals_review_add, row.includeLabel())
        val skipped = state.withInclude(false).toChoice(row)
        assertTrue(skipped.skip)
        assertTrue(listOf(skipped).writesNothing())
        assertEquals(emptyList<ShoppingWrite>(), ingredientReviewWrites(listOf(row), listOf(skipped)))

        val edited = state.copy(quantity = "1 kg").withStore(Supermarket.MERCADONA)
        assertEquals(edited, edited.withInclude(false).withInclude(true))

        fun rowFor(ingredient: Ingredient, items: List<ShoppingItem>) =
            buildIngredientReviewRows(listOf(ingredient), items, data, setOf(pantryId("Sal"))).single()
        assertEquals(R.string.meals_review_add, rowFor(Ingredient("Sal"), emptyList()).includeLabel())
        assertEquals(
            R.string.meals_review_sum,
            buildIngredientReviewRows(listOf(Ingredient("Sal")), listOf(item("s1", "Sal")), data).single()
                .includeLabel(),
        )
    }

    @Test
    fun `an omitted pantry row with a long free brand does not block confirming`() {
        val row = IngredientReviewRow(
            name = "Arroz",
            recipeQuantity = "300 g",
            existing = null,
            context = IngredientShoppingContext(arroz, emptyList(), emptyList()),
            inPantry = true,
        )
        val omitted = row.initialState().withFreeBrand("x".repeat(61))

        assertFalse(omitted.brandTooLong(row))
        assertTrue(canConfirmReview(listOf(row), listOf(omitted)))
        assertTrue(omitted.withInclude(true).brandTooLong(row))
    }
}
