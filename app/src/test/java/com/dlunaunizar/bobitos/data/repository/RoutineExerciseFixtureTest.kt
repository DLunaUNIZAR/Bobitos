package com.dlunaunizar.bobitos.data.repository

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoutineExerciseFixtureTest {
    // La misma tabla que tests/catalog-routines.test.mjs: lo que escribe el importador, la app lo lee y lo
    // vuelve a escribir igual (segundos, minutos, nivel y pesos vacíos incluidos).
    private val cases = File("../tests/fixtures/routine-exercises.json").also {
        assertTrue("falta la tabla compartida en ${it.absolutePath}", it.exists())
    }.readText().let(::JSONArray)

    @Test
    fun `the shared table covers the four measures`() {
        val names = (0 until cases.length()).map { cases.getJSONObject(it).getString("caso") }
        assertEquals(listOf("repeticiones", "segundos", "cardio", "otros"), names)
    }

    @Test
    fun `the app reads and rewrites imported exercises unchanged`() {
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val stored = case.getJSONObject("firestore").toPlainMap()
            val parsed = parseRoutineExercises(listOf(stored)).orEmpty()
            assertEquals(case.getString("caso"), listOf(stored), parsed.toFirestoreExercises())
        }
    }
}

private fun JSONObject.toPlainMap(): Map<String, Any?> = keys().asSequence().associateWith { key -> get(key).toPlain() }

private fun Any?.toPlain(): Any? = when (this) {
    JSONObject.NULL -> null
    is JSONObject -> toPlainMap()
    is JSONArray -> (0 until length()).map { get(it).toPlain() }
    else -> this
}
