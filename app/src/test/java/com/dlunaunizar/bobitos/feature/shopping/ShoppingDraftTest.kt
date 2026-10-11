package com.dlunaunizar.bobitos.feature.shopping

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingDraftTest {
    private val leche = CatalogIngredient(id = "leche", name = "Leche")
    private val pascual = IngredientBrand(
        id = "b1",
        ingredientId = "leche",
        name = "Pascual",
        favorite = true,
        stores = listOf(Supermarket.MERCADONA),
    )
    private val hacendado = IngredientBrand(id = "b2", ingredientId = "leche", name = "Hacendado")
    private val context = IngredientShoppingContext(
        ingredient = leche,
        brands = listOf(pascual, hacendado),
        stores = listOf(Supermarket.CARREFOUR),
    )

    @Test
    fun unBorradorNuevoEmpiezaVacio() {
        assertEquals(ShoppingDraft("", "", "", null, ""), ShoppingDraft.of(null))
    }

    @Test
    fun `a prefilled draft has only the name`() {
        assertEquals(ShoppingDraft("Tomate", "", "", null, ""), ShoppingDraft.prefilled("Tomate"))
    }

    @Test
    fun `recognizing an ingredient applies its default brand and store once`() {
        val draft = ShoppingDraft.of(null).withContext(context)

        assertEquals("leche", draft.pickerFor)
        assertEquals("b1", draft.brandId)
        assertEquals(Supermarket.MERCADONA, draft.supermarket)
        assertEquals("Pascual", draft.brand)
    }

    @Test
    fun `the same ingredient again keeps the user choice`() {
        val chosen = ShoppingDraft.of(null).withContext(context).withBrandChoice(context, "b2")

        assertEquals(chosen, chosen.withContext(context))
        assertEquals("Hacendado", chosen.brand)
    }

    @Test
    fun `leaving the ingredient drops the picker brand but keeps the store`() {
        val draft = ShoppingDraft.of(null).withContext(context).withContext(null)

        assertNull(draft.pickerFor)
        assertNull(draft.brandId)
        assertEquals("", draft.brand)
        assertEquals(Supermarket.MERCADONA, draft.supermarket)
    }

    @Test
    fun `typing past an ingredient into another does not carry its brand`() {
        val tomate = IngredientShoppingContext(
            ingredient = CatalogIngredient(id = "tomate", name = "Tomate"),
            brands = listOf(
                IngredientBrand(id = "v", ingredientId = "tomate", name = "Valor", favorite = true),
            ),
            stores = emptyList(),
        )
        val frito = IngredientShoppingContext(
            ingredient = CatalogIngredient(id = "frito", name = "Tomate frito"),
            brands = emptyList(),
            stores = emptyList(),
        )
        val draft = ShoppingDraft.of(null).withContext(tomate).withContext(null).withContext(frito)

        assertEquals("", draft.brand)
        assertNull(draft.brandId)
        assertEquals("", ShoppingDraft.of(null).withContext(tomate).withContext(frito).brand)
    }

    @Test
    fun `a hand-typed brand survives leaving and entering ingredients`() {
        val typed = ShoppingDraft.of(null).copy(brand = "Mi marca")

        assertEquals("Mi marca", typed.withContext(null).brand)
        assertEquals("Mi marca", typed.withContext(context.copy(brands = emptyList())).brand)
        assertEquals("Mi marca", typed.withContext(context.copy(brands = emptyList())).withContext(null).brand)
    }

    @Test
    fun `defaults are refreshed when brands arrive later for an untouched draft`() {
        val early = ShoppingDraft.of(null).withContext(context.copy(brands = emptyList()))
        val refreshed = early.withContext(context)

        assertEquals("b1", refreshed.brandId)
        assertEquals("Pascual", refreshed.brand)
        assertEquals(Supermarket.MERCADONA, refreshed.supermarket)
    }

    @Test
    fun `a user choice is kept when brands change`() {
        val chosen = ShoppingDraft.of(null).withContext(context).withBrandChoice(context, "b2")
        val other = context.copy(brands = listOf(hacendado, pascual.copy(name = "Pascual 2")))
        val after = chosen.withContext(other)

        assertEquals("b2", after.brandId)
        assertEquals("Hacendado", after.brand)
    }

    @Test
    fun `a renamed brand updates the text of an untouched default`() {
        val first = ShoppingDraft.of(null).withContext(context)
        val after = first.withContext(context.copy(brands = listOf(pascual.copy(name = "Pascual 2"), hacendado)))

        assertEquals("Pascual 2", after.brand)
    }

    @Test
    fun `a deleted brand falls back to indifferent`() {
        val chosen = ShoppingDraft.of(null).withContext(context).withBrandChoice(context, "b2")
        val after = chosen.withContext(context.copy(brands = listOf(pascual)))

        assertNull(after.brandId)
        assertEquals("", after.brand)
    }

    @Test
    fun `a prefilled draft is not dirty after defaults apply or refresh`() {
        val initial = ShoppingDraft.prefilled("Leche")
        val noBrands = context.copy(brands = emptyList())
        val applied = initial.withContext(noBrands)
        val refreshed = applied.withContext(context)

        assertFalse(applied.isDirty(initial, noBrands))
        assertFalse(refreshed.isDirty(initial, context))
        assertFalse(initial.isDirty(initial, null))
        assertTrue(refreshed.withBrandChoice(context, "b2").isDirty(initial, context))
    }

    @Test
    fun `the saved brand comes from the picker when there are brands`() {
        val draft = ShoppingDraft.of(null).withContext(context).copy(brand = "texto viejo")

        assertEquals("Pascual", draft.savedBrand(context))
        assertEquals("texto viejo", draft.savedBrand(null))
        assertNull(draft.withBrandChoice(context, null).savedBrand(context))
    }

    @Test
    fun `an ingredient without brands keeps the typed brand and takes its first store`() {
        val noBrands = context.copy(brands = emptyList())
        val draft = ShoppingDraft.of(null).copy(brand = "Mi marca").withContext(noBrands)

        assertEquals("Mi marca", draft.brand)
        assertNull(draft.brandId)
        assertEquals(Supermarket.CARREFOUR, draft.supermarket)
    }

    @Test
    fun `choosing indifferent stores no brand text`() {
        val draft = ShoppingDraft.of(null).withContext(context).withBrandChoice(context, null)

        assertEquals("", draft.brand)
        assertNull(draft.brandId)
    }

    @Test
    fun `choosing a brand writes its name as the brand text`() {
        val draft = ShoppingDraft.of(null).withContext(context).withBrandChoice(context, "b2")

        assertEquals("b2", draft.brandId)
        assertEquals("Hacendado", draft.brand)
    }
}
