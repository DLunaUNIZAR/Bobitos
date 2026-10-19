package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ShoppingFirestoreTest {
    private val seen = ShoppingItem(
        id = "s1",
        name = "Leche",
        quantity = "1 l",
        notes = null,
        purchased = true,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
        purchasedBy = "u",
        purchasedByName = "U",
        purchasedAt = Instant.EPOCH,
        supermarket = null,
        brand = null,
    )

    private val me = AuthUser(id = "me", displayName = "Yo", email = "yo@example.com", isEmailVerified = true)
    private val values = ItemValues(name = "Leche", quantity = "1 l", notes = null, supermarket = null, brand = null)

    @Test
    fun `a restored purchased item is written purchased by whoever undoes`() {
        val data = newItemFields(me, values, purchased = true)

        assertEquals(true, data["purchased"])
        assertEquals("me", data["purchasedBy"])
        assertEquals("Yo", data["purchasedByName"])
        assertEquals(FieldValue.serverTimestamp(), data["purchasedAt"])
    }

    @Test
    fun `a new item is written unpurchased`() {
        val data = newItemFields(me, values)

        assertEquals(false, data["purchased"])
        assertNull(data["purchasedBy"])
        assertNull(data["purchasedByName"])
        assertNull(data["purchasedAt"])
    }

    @Test
    fun `an item still as it was seen is not stale`() {
        assertNull(staleItemFailure(seen.copy(updatedBy = "otro", updatedAt = Instant.now()), seen))
    }

    @Test
    fun `a changed item fails as changed and says which one, as it was seen`() {
        val failure = staleItemFailure(seen.copy(name = "Leche entera", quantity = "2 l"), seen)!!

        assertEquals(ShoppingFailure.ItemChanged, failure.failure)
        assertEquals("Leche", failure.itemName)
    }

    @Test
    fun `a deleted item fails as not found and says which one`() {
        val failure = staleItemFailure(null, seen)!!

        assertEquals(ShoppingFailure.ItemNotFound, failure.failure)
        assertEquals("Leche", failure.itemName)
    }

    private fun listed(name: String, purchased: Boolean = false) =
        seen.copy(id = "id-$name", name = name, purchased = purchased)

    @Test
    fun `adding what is still not on the list is fine`() {
        assertNull(listedMeanwhileFailure(listOf(listed("Pan")), listOf("Leche", "Arroz")))
    }

    @Test
    fun `if someone has listed something being added it fails as changed and names it as listed`() {
        val failure = listedMeanwhileFailure(listOf(listed("Pan"), listed("Tomates")), listOf("Leche", "tomate"))!!

        assertEquals(ShoppingFailure.ItemChanged, failure.failure)
        assertEquals("Tomates", failure.itemName)
    }

    @Test
    fun `an item listed and bought meanwhile also fails, so that tapping done again lists it again`() {
        val failure = listedMeanwhileFailure(listOf(listed("Leche", purchased = true)), listOf("Leche"))!!

        assertEquals(ShoppingFailure.ItemChanged, failure.failure)
        assertEquals("Leche", failure.itemName)
    }
}
