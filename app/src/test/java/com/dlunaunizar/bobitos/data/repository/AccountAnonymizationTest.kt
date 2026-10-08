package com.dlunaunizar.bobitos.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AccountAnonymizationTest {
    // Cada colección de un espacio cuyas reglas permiten anonimizar el nombre debe recorrerse al borrar
    // la cuenta; si no, el nombre de la cuenta borrada se queda (y se copia a otros documentos).
    @Test
    fun `deleting an account anonymizes every space collection the rules allow`() {
        val rules = listOf(File("../firestore.rules"), File("firestore.rules")).first(File::exists).readText()
        // Cada bloque va desde su `match /coleccion/{id}` hasta el siguiente `match`; cuenta si su
        // `allow update` admite una anonimización (no la definición de la función).
        val usesAnonymization = Regex("""\|\|\s*valid\w+Anonymization\(spaceId\)""")
        val allowedByRules = rules.split("match /").drop(1)
            .filter { block -> usesAnonymization.containsMatchIn(block) }
            .map { block -> block.substringBefore('/') }
            .toSet()

        assertEquals(allowedByRules, ANONYMIZED_SPACE_COLLECTIONS.toSet())
    }
}
