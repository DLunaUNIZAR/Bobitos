package com.dlunaunizar.bobitos.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PersonalDataPathsTest {
    // Lo personal que se borra con la cuenta debe ser justo lo que las reglas dan al dueño bajo users/{uid}.
    @Test
    fun `personal collections are the ones the rules give to the owner`() {
        val rules = listOf(File("../firestore.rules"), File("firestore.rules")).first(File::exists).readText()
        assertTrue(rules.contains("match /users/{userId}"))
        PERSONAL_USER_COLLECTIONS.forEach { name ->
            assertTrue("falta match /users/{userId}/$name/{", rules.contains("match /users/{userId}/$name/{"))
        }
        assertEquals(listOf("ingredients", "brands"), PERSONAL_USER_COLLECTIONS)
    }

    @Test
    fun `account deletion also clears ingredientPrefs`() {
        assertEquals("ingredientPrefs", INGREDIENT_PREFS_COLLECTION)
    }
}
