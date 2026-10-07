package com.dlunaunizar.bobitos.feature.shopping

import androidx.compose.runtime.saveable.SaverScope
import com.dlunaunizar.bobitos.core.model.Supermarket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShoppingDraftTest {
    private val allSaveable = SaverScope { true }

    private fun roundTrip(draft: ShoppingDraft): ShoppingDraft? = with(ShoppingDraftSaver) {
        restore(allSaveable.save(draft)!!)
    }

    @Test
    fun elBorradorCompletoSobreviveAUnaRotacion() {
        val draft = ShoppingDraft(
            name = "Leche",
            quantity = "2 l",
            notes = "sin lactosa",
            supermarketName = Supermarket.entries.first().name,
            brand = "Pascual",
        )
        assertEquals(draft, roundTrip(draft))
    }

    @Test
    fun elBorradorSinSupermercadoSobreviveAUnaRotacion() {
        val draft = ShoppingDraft("", "", "", null, "")
        assertEquals(draft, roundTrip(draft))
    }

    @Test
    fun elSupermercadoSeReconstruyeDesdeSuNombre() {
        val market = Supermarket.entries.first()
        assertEquals(market, ShoppingDraft("x", "", "", market.name, "").supermarket)
        assertNull(ShoppingDraft("x", "", "", null, "").supermarket)
    }

    @Test
    fun unBorradorNuevoEmpiezaVacio() {
        assertEquals(ShoppingDraft("", "", "", null, ""), ShoppingDraft.of(null))
    }
}
