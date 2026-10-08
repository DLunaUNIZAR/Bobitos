package com.dlunaunizar.bobitos.feature.ingredients

import androidx.compose.runtime.saveable.SaverScope
import com.dlunaunizar.bobitos.core.model.Nutrition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrandDraftTest {
    private val allSaveable = SaverScope { true }

    private fun roundTrip(draft: BrandDraft): BrandDraft? = with(BrandDraftSaver) {
        restore(allSaveable.save(draft)!!)
    }

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
    fun elBorradorSobreviveAUnaRotacion() {
        val draft = BrandDraft.of("Hacendado", "8480000", Nutrition(energyKcal = 120.0, protein = 3.0))
        assertEquals(draft, roundTrip(draft))
        val empty = BrandDraft.of("", "", null)
        assertEquals(empty, roundTrip(empty))
    }
}
