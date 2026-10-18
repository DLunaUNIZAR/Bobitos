package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.findByKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IngredientListLogicTest {
    @Test
    fun `merge hides a personal ingredient whose id is common and sorts by name`() {
        val common = listOf(CatalogIngredient("tomate", "Tomate"), CatalogIngredient("arroz", "Arroz"))
        val personal = listOf(
            CatalogIngredient("tomate", "Tomate mío", personal = true),
            CatalogIngredient("canela", "Canela", personal = true),
        )

        val merged = mergeIngredients(common, personal)

        assertEquals(listOf("Arroz", "Canela", "Tomate"), merged.map(CatalogIngredient::name))
        assertEquals(false, merged.single { it.id == "tomate" }.personal)
    }

    @Test
    fun `findByKey matches plural, accents and case`() {
        val list = listOf(CatalogIngredient("limon", "Limón"), CatalogIngredient("arroz", "Arroz"))

        assertEquals("limon", findByKey(list, "  LIMONES ")?.id)
        assertNull(findByKey(list, "pera"))
    }

    @Test
    fun `findByKey ignores blank names`() {
        assertNull(findByKey(listOf(CatalogIngredient("a", "A")), "   "))
        assertNull(findByKey(listOf(CatalogIngredient("x", "de la")), "de la"))
    }

    @Test
    fun `row line shows the first favorite with its store or the ingredient store`() {
        val brands = listOf(
            brand("b", "Zeta", favorite = true, stores = listOf(Supermarket.DIA)),
            brand("a", "Valor", favorite = true, stores = listOf(Supermarket.MERCADONA)),
            brand("c", "Otra"),
        )
        assertEquals(IngredientRowLine.Favorite("Valor", Supermarket.MERCADONA), ingredientRowLine(brands, null))

        val noStore = listOf(brand("a", "Valor", favorite = true))
        val pref = IngredientPref(listOf(Supermarket.DIA))
        assertEquals(IngredientRowLine.Favorite("Valor", Supermarket.DIA), ingredientRowLine(noStore, pref))
        assertEquals(IngredientRowLine.Favorite("Valor", null), ingredientRowLine(noStore, null))
    }

    @Test
    fun `row line counts brands when none is favorite`() {
        val brands = listOf(brand("a", "A"), brand("b", "B"))

        assertEquals(IngredientRowLine.Brands(2), ingredientRowLine(brands, IngredientPref(listOf(Supermarket.DIA))))
    }

    @Test
    fun `row line falls back to the ingredient first store and then to nothing`() {
        assertEquals(
            IngredientRowLine.Store(Supermarket.DIA),
            ingredientRowLine(emptyList(), IngredientPref(listOf(Supermarket.DIA, Supermarket.MERCADONA))),
        )
        assertEquals(IngredientRowLine.None, ingredientRowLine(emptyList(), IngredientPref()))
        assertEquals(IngredientRowLine.None, ingredientRowLine(emptyList(), null))
    }

    @Test
    fun `matching uses name and category ignoring accents`() {
        val list = listOf(
            CatalogIngredient("limon", "Limón", category = "Fruta"),
            CatalogIngredient("arroz", "Arroz", category = "Cereales"),
        )

        assertEquals(listOf("limon"), list.matching("limon").map(CatalogIngredient::id))
        assertEquals(listOf("arroz"), list.matching("CEREAL").map(CatalogIngredient::id))
        assertEquals(2, list.matching(" ").size)
    }

    @Test
    fun `las categorias salen ordenadas y sin repetir`() {
        val list = listOf(
            CatalogIngredient("a", "Leche", category = "Lácteos"),
            CatalogIngredient("b", "Queso", category = "lácteos "),
            CatalogIngredient("c", "Pera", category = "Frutas"),
            CatalogIngredient("d", "Sal", category = null),
            CatalogIngredient("e", "Agua", category = "  "),
        )

        assertEquals(listOf("Frutas", "Lácteos"), list.categories())
    }

    @Test
    fun `filtra por categoria sin mayusculas ni espacios en los extremos`() {
        val list = listOf(
            CatalogIngredient("a", "Leche", category = "Lácteos"),
            CatalogIngredient("b", "Pera", category = "Frutas"),
            CatalogIngredient("c", "Sal", category = null),
        )

        assertEquals(listOf("a"), list.inCategory(" lácteos").map(CatalogIngredient::id))
        assertEquals(emptyList<String>(), list.inCategory("Carnes").map(CatalogIngredient::id))
    }

    @Test
    fun `sin categoria elegida devuelve todo`() {
        val list = listOf(CatalogIngredient("a", "Leche", category = "Lácteos"), CatalogIngredient("c", "Sal"))

        assertEquals(list, list.inCategory(null))
    }

    private fun brand(id: String, name: String, favorite: Boolean = false, stores: List<Supermarket> = emptyList()) =
        IngredientBrand(id = id, ingredientId = "x", name = name, favorite = favorite, stores = stores)
}
