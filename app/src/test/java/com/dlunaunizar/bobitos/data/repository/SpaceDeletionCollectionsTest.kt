package com.dlunaunizar.bobitos.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpaceDeletionCollectionsTest {
    // Borrar un espacio tiene que vaciar todas sus subcolecciones: si no, sus documentos se quedan huérfanos en
    // Firestore (pasaba con las notas y las sesiones de deporte).
    @Test
    fun `deleting a space empties every subcollection the rules open inside it`() {
        val rules = listOf(File("../firestore.rules"), File("firestore.rules")).first(File::exists).readText()
        val header = "match /spaces/{spaceId} {"
        val start = rules.indexOf(header)
        val end = rules.indexOf("match /{document=**}", start)
        assertTrue("no se encuentra el bloque de spaces en firestore.rules", start >= 0 && end > start)
        val inRules = Regex("""match /(\w+)/\{""").findAll(rules.substring(start + header.length, end))
            .map { it.groupValues[1] }
            .toSet()

        assertEquals(inRules, SPACE_CHILD_COLLECTIONS.toSet())
    }
}
