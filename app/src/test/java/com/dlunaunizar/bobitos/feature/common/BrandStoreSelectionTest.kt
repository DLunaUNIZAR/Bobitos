package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Supermarket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrandStoreSelectionTest {
    private val tomate = CatalogIngredient(id = "tomate", name = "Tomate")

    private fun brand(
        id: String,
        favorite: Boolean = false,
        stores: List<Supermarket> = emptyList(),
        ingredient: String = "tomate",
    ) = IngredientBrand(id = id, ingredientId = ingredient, name = id.uppercase(), favorite = favorite, stores = stores)

    private fun context(
        brands: List<IngredientBrand> = emptyList(),
        stores: List<Supermarket> = emptyList(),
    ): IngredientShoppingContext {
        val data = IngredientShoppingData(
            ingredients = listOf(tomate),
            brands = brands,
            prefs = if (stores.isEmpty()) emptyMap() else mapOf("tomate" to IngredientPref(stores)),
        )
        return checkNotNull(data.contextFor("Tomate"))
    }

    @Test
    fun `a name matching by key finds the ingredient and only its brands, favorites first`() {
        val data = IngredientShoppingData(
            ingredients = listOf(tomate, CatalogIngredient(id = "pasta", name = "Pasta")),
            brands = listOf(brand("a"), brand("b", favorite = true), brand("x", ingredient = "pasta")),
            prefs = mapOf("tomate" to IngredientPref(listOf(Supermarket.DIA))),
        )
        val ctx = data.contextFor("Tomates")!!
        assertEquals("tomate", ctx.ingredient.id)
        assertEquals(listOf("b", "a"), ctx.brands.map { it.id })
        assertEquals(listOf(Supermarket.DIA), ctx.stores)
    }

    @Test
    fun `with two ingredients of the same key the first in the list wins`() {
        val data = IngredientShoppingData(
            ingredients = listOf(
                CatalogIngredient(id = "first", name = "Tomate"),
                CatalogIngredient(id = "second", name = "Tomates"),
            ),
        )
        assertEquals("first", data.contextFor("tomate")!!.ingredient.id)
    }

    @Test
    fun `a name matching nothing has no context`() {
        assertNull(IngredientShoppingData(ingredients = listOf(tomate)).contextFor("Lechuga"))
    }

    @Test
    fun `a blank name has no context`() {
        assertNull(IngredientShoppingData(ingredients = listOf(tomate)).contextFor("  "))
    }

    @Test
    fun `a single favorite is the default with its first store`() {
        val ctx = context(
            brands = listOf(
                brand("a", stores = listOf(Supermarket.DIA)),
                brand("b", true, listOf(Supermarket.EROSKI, Supermarket.DIA)),
            ),
            stores = listOf(Supermarket.MERCADONA),
        )
        assertEquals(BrandStoreSelection("b", Supermarket.EROSKI), ctx.defaultSelection())
    }

    @Test
    fun `a favorite without stores falls back to the ingredient first store`() {
        val ctx = context(brands = listOf(brand("b", true)), stores = listOf(Supermarket.MERCADONA, Supermarket.DIA))
        assertEquals(BrandStoreSelection("b", Supermarket.MERCADONA), ctx.defaultSelection())
    }

    @Test
    fun `two favorites or none default to indifferent and the ingredient first store`() {
        val stores = listOf(Supermarket.ALCAMPO)
        val two = context(brands = listOf(brand("a", true), brand("b", true)), stores = stores)
        val none = context(brands = listOf(brand("a"), brand("b")), stores = stores)
        assertEquals(BrandStoreSelection(null, Supermarket.ALCAMPO), two.defaultSelection())
        assertEquals(BrandStoreSelection(null, Supermarket.ALCAMPO), none.defaultSelection())
    }

    @Test
    fun `no brands and no stores default to nothing`() {
        assertEquals(BrandStoreSelection(null, null), context().defaultSelection())
    }

    @Test
    fun `changing brand takes the new brand first store`() {
        val ctx =
            context(
                brands = listOf(
                    brand("a", stores = listOf(Supermarket.DIA)),
                    brand("b", stores = listOf(Supermarket.EROSKI)),
                ),
            )
        assertEquals(
            BrandStoreSelection("b", Supermarket.EROSKI),
            ctx.selectBrand(BrandStoreSelection("a", Supermarket.DIA), "b"),
        )
    }

    @Test
    fun `changing to a brand without stores resets a store that came from the previous brand`() {
        val ctx = context(
            brands = listOf(brand("a", stores = listOf(Supermarket.DIA)), brand("b")),
            stores = listOf(Supermarket.MERCADONA),
        )
        assertEquals(
            BrandStoreSelection("b", Supermarket.MERCADONA),
            ctx.selectBrand(BrandStoreSelection("a", Supermarket.DIA), "b"),
        )
    }

    @Test
    fun `changing to a brand without stores keeps a store the user chose elsewhere`() {
        val ctx = context(
            brands = listOf(brand("a", stores = listOf(Supermarket.DIA)), brand("b")),
            stores = listOf(Supermarket.MERCADONA),
        )
        assertEquals(
            BrandStoreSelection("b", Supermarket.ALCAMPO),
            ctx.selectBrand(BrandStoreSelection("a", Supermarket.ALCAMPO), "b"),
        )
    }

    @Test
    fun `changing to indifferent behaves like a brand without stores`() {
        val ctx =
            context(
                brands = listOf(brand("a", stores = listOf(Supermarket.DIA))),
                stores = listOf(Supermarket.MERCADONA),
            )
        assertEquals(
            BrandStoreSelection(null, Supermarket.MERCADONA),
            ctx.selectBrand(BrandStoreSelection("a", Supermarket.DIA), null),
        )
        assertEquals(
            BrandStoreSelection(null, Supermarket.ALCAMPO),
            ctx.selectBrand(BrandStoreSelection("a", Supermarket.ALCAMPO), null),
        )
    }

    @Test
    fun `store options list brand stores, then ingredient stores, then the rest without repeats`() {
        val ctx = context(
            brands = listOf(brand("a", stores = listOf(Supermarket.OTROS, Supermarket.DIA))),
            stores = listOf(Supermarket.DIA, Supermarket.ALCAMPO),
        )
        assertEquals(
            listOf(
                Supermarket.OTROS,
                Supermarket.DIA,
                Supermarket.ALCAMPO,
                Supermarket.EROSKI,
                Supermarket.MERCADONA,
                Supermarket.CARREFOUR,
                Supermarket.CARNICERIA,
                Supermarket.PESCADERIA,
                Supermarket.FRUTERIA,
                Supermarket.PANADERIA,
                Supermarket.INDIFERENTE,
            ),
            ctx.storeOptions(BrandStoreSelection("a", null)),
        )
    }

    @Test
    fun `brand name is null for indifferent and for an unknown id`() {
        val ctx = context(brands = listOf(brand("a")))
        assertEquals("A", ctx.brandName(BrandStoreSelection("a", null)))
        assertNull(ctx.brandName(BrandStoreSelection(null, null)))
        assertNull(ctx.brandName(BrandStoreSelection("gone", null)))
    }
}
