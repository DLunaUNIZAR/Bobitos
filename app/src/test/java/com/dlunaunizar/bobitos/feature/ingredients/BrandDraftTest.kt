package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.Nutrition
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.repository.BrandInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrandDraftTest {
    @Test
    fun unaMarcaNuevaEmpiezaVaciaYSinNutricion() {
        val draft = BrandDraft.of("", "", null)
        assertEquals("", draft.name)
        assertEquals(List(6) { "" }, draft.nutrition)
        assertNull(draft.toNutrition())
    }

    @Test
    fun laNutricionSeMuestraSinDecimalesInnecesarios() {
        val draft = BrandDraft.of("Hacendado", "8480000", Nutrition(energyKcal = 120.0, fat = 1.5, salt = 0.25))
        assertEquals(listOf("120", "1.5", "", "", "", "0.25"), draft.nutrition)
        assertEquals("8480000", draft.barcode)
    }

    @Test
    fun alGuardarSeAceptaComaYPuntoDecimalYLoVacioEsNulo() {
        val draft = BrandDraft.of("x", "", null).withNutrition(0, "120").withNutrition(1, "1,5").withNutrition(4, " ")
        assertEquals(Nutrition(energyKcal = 120.0, fat = 1.5), draft.toNutrition())
    }

    @Test
    fun cambiarUnCampoNoTocaLosDemas() {
        val draft = BrandDraft.of("x", "", Nutrition(energyKcal = 100.0)).withNutrition(5, "2")
        assertEquals(listOf("100", "", "", "", "", "2"), draft.nutrition)
    }

    @Test
    fun `a draft from a brand keeps favorite and stores`() {
        val brand = IngredientBrand(
            id = "b1",
            ingredientId = "tomate",
            name = "Hacendado",
            barcode = "84",
            nutrition = Nutrition(energyKcal = 30.0),
            favorite = true,
            stores = listOf(Supermarket.DIA, Supermarket.ALCAMPO),
        )

        val draft = BrandDraft.of(brand)

        assertEquals("Hacendado", draft.name)
        assertEquals("84", draft.barcode)
        assertEquals(true, draft.favorite)
        assertEquals(listOf(Supermarket.DIA, Supermarket.ALCAMPO), draft.stores)
        assertEquals(Nutrition(energyKcal = 30.0), draft.toNutrition())
    }

    @Test
    fun `toInput carries ingredient, favorite, stores and nutrition`() {
        val draft = BrandDraft.of("  Hacendado ", " ", null)
            .copy(favorite = true, stores = listOf(Supermarket.EROSKI))
            .withNutrition(0, "30")

        assertEquals(
            BrandInput(
                ingredientId = "tomate",
                name = "Hacendado",
                barcode = null,
                nutrition = Nutrition(energyKcal = 30.0),
                favorite = true,
                stores = listOf(Supermarket.EROSKI),
            ),
            draft.toInput("tomate"),
        )
    }
}
