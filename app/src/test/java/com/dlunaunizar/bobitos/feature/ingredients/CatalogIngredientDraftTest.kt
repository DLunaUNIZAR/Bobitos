package com.dlunaunizar.bobitos.feature.ingredients

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class CatalogIngredientDraftTest {
    private fun ingredient(category: String?, unit: String?) = CatalogIngredient(
        id = "i1", name = "Arroz", category = category, defaultUnit = unit, ownerUid = "u",
        createdBy = "u", createdByName = "U", createdAt = Instant.EPOCH, updatedBy = "u", updatedAt = Instant.EPOCH,
    )

    @Test
    fun unIngredienteNuevoEmpiezaVacio() {
        assertEquals(CatalogIngredientDraft("", "", ""), CatalogIngredientDraft.of(null, ""))
    }

    @Test
    fun unIngredienteNuevoDesdeEscaneoTraeElNombreSugerido() {
        assertEquals("Leche", CatalogIngredientDraft.of(null, "Leche").name)
    }

    @Test
    fun editarCargaSusDatosYLosNulosSonVacios() {
        val full = CatalogIngredientDraft.of(ingredient("cereales", "g"), "")
        assertEquals(CatalogIngredientDraft("Arroz", "cereales", "g"), full)
        assertEquals(CatalogIngredientDraft("Arroz", "", ""), CatalogIngredientDraft.of(ingredient(null, null), "Otro"))
    }
}
