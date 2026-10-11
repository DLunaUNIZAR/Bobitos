package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogIngredientFirestoreTest {
    @Test
    fun `an imported common ingredient without owner fields is parsed`() {
        val data = mapOf<String, Any?>(
            "name" to "Arroz",
            "nameLower" to "arroz",
            "category" to "Cereales",
            "defaultUnit" to "g",
            "source" to "import",
            "createdAt" to "ts",
            "updatedAt" to "ts",
            "updatedBy" to "admin",
        )
        val parsed = parseCatalogIngredient("arroz", data, personal = false)
        assertEquals(CatalogIngredient("arroz", "Arroz", "Cereales", "g", personal = false), parsed)
    }

    @Test
    fun `a document without name or with blank name is dropped`() {
        assertNull(parseCatalogIngredient("a", mapOf("nameLower" to "a"), personal = false))
        assertNull(parseCatalogIngredient("a", mapOf("name" to "  "), personal = false))
        assertNull(parseCatalogIngredient("a", mapOf("name" to 3), personal = false))
    }

    @Test
    fun `blank or non-string category and unit become null`() {
        val parsed = parseCatalogIngredient("a", mapOf("name" to "A", "category" to " ", "defaultUnit" to 5), false)
        assertNull(parsed?.category)
        assertNull(parsed?.defaultUnit)
    }

    @Test
    fun `personal flag is carried`() {
        assertTrue(parseCatalogIngredient("a", mapOf("name" to "A"), personal = true)?.personal == true)
        assertFalse(parseCatalogIngredient("a", mapOf("name" to "A"), personal = false)?.personal == true)
    }

    @Test
    fun `catalog order ignores accents and case and ties by id`() {
        val sorted = listOf("Arroz", "árbol", "aceite").map { CatalogIngredient(it.lowercase(), it) }
            .sortedForIngredientCatalog()
        assertEquals(listOf("aceite", "árbol", "Arroz"), sorted.map { it.name })
        val ties = listOf(CatalogIngredient("b", "Sal"), CatalogIngredient("a", "sal")).sortedForIngredientCatalog()
        assertEquals(listOf("a", "b"), ties.map { it.id })
    }

    @Test
    fun `validation trims, nulls blanks and enforces limits`() {
        assertEquals(IngredientInput("Sal", null, "g"), validateIngredientInput(" Sal ", "  ", " g "))
        assertEquals(IngredientFailure.NameTooLong, failure { validateIngredientInput("a".repeat(121), null, null) })
        assertEquals(IngredientFailure.NameRequired, failure { validateIngredientInput("!!!", null, null) })
        assertEquals(IngredientFailure.NameRequired, failure { validateIngredientInput("  ", null, null) })
        assertEquals(IngredientFailure.CategoryTooLong, failure { validateIngredientInput("A", "c".repeat(61), null) })
        assertEquals(IngredientFailure.UnitTooLong, failure { validateIngredientInput("A", null, "u".repeat(25)) })
    }

    @Test
    fun `firestore fields carry nameLower`() {
        assertEquals(
            mapOf("name" to "Arroz Largo", "nameLower" to "arroz largo", "category" to null, "defaultUnit" to "g"),
            IngredientInput("Arroz Largo", null, "g").toFirestoreFields(),
        )
    }

    private fun failure(block: () -> Unit): IngredientFailure =
        assertThrows(IngredientRepositoryException::class.java, block).failure
}
