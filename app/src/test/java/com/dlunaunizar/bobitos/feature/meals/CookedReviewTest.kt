package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.pantryId
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CookedReviewTest {
    @Test
    fun `pending items of the recipe go to cross off and what is in the pantry to finished`() {
        val review = cookedReview(
            "Lentejas",
            listOf(Ingredient("Lentejas", "300", "g"), Ingredient("Chorizo"), Ingredient("Sal")),
            listOf(item("s1", "lentejas"), item("s2", "Leche"), item("s3", "Chorizo", purchased = true)),
            setOf(pantryId("Sal"), pantryId("Chorizo"), pantryId("Leche")),
        )!!

        assertEquals("Lentejas", review.mealName)
        assertEquals(listOf("s1"), review.crossOff.map(ShoppingItem::id))
        assertEquals(
            listOf(CookedPantryRow(pantryId("Chorizo"), "Chorizo"), CookedPantryRow(pantryId("Sal"), "Sal")),
            review.finished,
        )
    }

    @Test
    fun `an ingredient pending in the list only shows in cross off`() {
        val review = cookedReview(
            "Ensalada",
            listOf(Ingredient("Tomates")),
            listOf(item("s1", "tomate")),
            setOf(pantryId("Tomate")),
        )!!

        assertEquals(listOf("s1"), review.crossOff.map(ShoppingItem::id))
        assertTrue(review.finished.isEmpty())
    }

    @Test
    fun `repeated ingredients show once and names without letters or numbers never`() {
        val review = cookedReview(
            "Sopa de ajo",
            listOf(
                Ingredient("Ajo"),
                Ingredient("ajos"),
                Ingredient("Tomates"),
                Ingredient("tomate"),
                Ingredient("—"),
                Ingredient("  "),
            ),
            emptyList(),
            setOf(pantryId("Ajo"), pantryId("Tomate"), ""),
        )!!

        assertEquals(listOf("Ajo", "Tomates"), review.finished.map(CookedPantryRow::name))
    }

    @Test
    fun `without the list or the pantry their section does not show, and with nothing to offer there is no dialog`() {
        val ingredients = listOf(Ingredient("Arroz"), Ingredient("Sal"))

        val noPantry = cookedReview("Paella", ingredients, listOf(item("s1", "Arroz")), null)!!
        assertEquals(listOf("s1"), noPantry.crossOff.map(ShoppingItem::id))
        assertTrue(noPantry.finished.isEmpty())

        val noList = cookedReview("Paella", ingredients, null, setOf(pantryId("Arroz")))!!
        assertTrue(noList.crossOff.isEmpty())
        assertEquals(listOf("Arroz"), noList.finished.map(CookedPantryRow::name))

        assertNull(cookedReview("Paella", ingredients, null, null))
        assertNull(cookedReview("Paella", ingredients, emptyList(), emptySet()))
    }

    @Test
    fun `by default everything pending is crossed off, nothing is finished and restock is on`() {
        val review = CookedReview(
            "Paella",
            listOf(item("s1", "Arroz"), item("s2", "Gambas")),
            listOf(CookedPantryRow("sal", "Sal")),
        )

        assertEquals(CookedChoice(setOf("s1", "s2"), emptySet(), restock = true), review.defaultChoice())
    }

    @Test
    fun `the writes follow the choice and restock only names what is finished`() {
        val review = CookedReview(
            "Paella",
            listOf(item("s1", "Arroz"), item("s2", "Gambas")),
            listOf(CookedPantryRow("sal", "Sal"), CookedPantryRow("aceit-oliva", "Aceite de oliva")),
        )
        val choice = review.defaultChoice().toggleCrossOff("s2").toggleFinished("sal")

        val writes = review.writesFor(choice)

        assertEquals(listOf("s1"), writes.crossOff.map(ShoppingItem::id))
        assertEquals(listOf("sal"), writes.finishedIds)
        assertEquals(listOf("Sal"), writes.restockNames)
        assertEquals(emptyList<String>(), review.writesFor(choice.copy(restock = false)).restockNames)
        assertTrue(review.writesFor(CookedChoice(emptySet(), emptySet(), restock = true)).isEmpty)
    }

    private fun item(id: String, name: String, purchased: Boolean = false) = ShoppingItem(
        id = id,
        name = name,
        quantity = null,
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
    )

    @Test
    fun `what is put on the list takes the catalog name when it is recognized and stays as written if not`() {
        val data = IngredientShoppingData(
            listOf(
                CatalogIngredient("aceite-de-oliva-virgen-extra", "Aceite de oliva virgen extra"),
                CatalogIngredient("huevos", "Huevos"),
            ),
        )
        val writes = CookedWrites(
            crossOff = emptyList(),
            finishedIds = listOf("a", "b", "c"),
            restockNames = listOf("aceite de oliva virgen extra", "huevo", "Sal de Añana"),
        )

        assertEquals(
            listOf("Aceite de oliva virgen extra", "Huevos", "Sal de Añana"),
            writes.restockNamesIn(data),
        )
    }
}
