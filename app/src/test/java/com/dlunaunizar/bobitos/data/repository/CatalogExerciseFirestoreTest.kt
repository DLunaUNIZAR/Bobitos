package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseEquipment
import com.dlunaunizar.bobitos.core.model.ExerciseInput
import com.dlunaunizar.bobitos.core.model.ExerciseSource
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.SetMeasure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class CatalogExerciseFirestoreTest {
    private val created = Instant.parse("2026-01-01T00:00:00Z")
    private val updated = Instant.parse("2026-02-01T00:00:00Z")

    private fun legacy(): Map<String, Any?> = mapOf(
        "name" to "Press banca",
        "type" to "PESO_LIBRE",
        "muscleGroup" to "Pecho",
        "ownerUid" to "u1",
        "createdBy" to "u1",
        "createdByName" to "Ana",
        "updatedBy" to "u1",
    )

    private fun parse(data: Map<String, Any?>, createdAt: Instant? = created) =
        parseCatalogExercise("press-banca", data, createdAt, updated)

    private fun exercise(id: String, name: String) = CatalogExercise(
        id = id, name = name, type = ExerciseType.OTROS, ownerUid = "u", createdBy = "u", createdByName = "U",
        createdAt = created, updatedBy = "u", updatedAt = created,
    )

    @Test
    fun `legacy document without description, equipment or source parses with defaults`() {
        val parsed = parse(legacy())!!
        assertEquals("Press banca", parsed.name)
        assertEquals(ExerciseType.PESO_LIBRE, parsed.type)
        assertNull(parsed.description)
        assertEquals(emptyList<ExerciseEquipment>(), parsed.equipment)
        assertNull(parsed.source)
    }

    @Test
    fun `description, equipment and wger source are parsed`() {
        val parsed = parse(
            legacy() + mapOf(
                "description" to "Tumbado en el banco.",
                "equipment" to listOf("BARRA", "BANCO"),
                "source" to mapOf(
                    "provider" to "wger",
                    "id" to 192L,
                    "license" to "CC-BY-SA-4.0",
                    "author" to "Ana",
                    "url" to "https://wger.de/es/exercise/192/view/",
                    "importedAt" to "ignorado",
                ),
            ),
        )!!
        assertEquals("Tumbado en el banco.", parsed.description)
        assertEquals(listOf(ExerciseEquipment.BARRA, ExerciseEquipment.BANCO), parsed.equipment)
        assertEquals(
            ExerciseSource("wger", 192L, "CC-BY-SA-4.0", "Ana", "https://wger.de/es/exercise/192/view/"),
            parsed.source,
        )
    }

    @Test
    fun `unknown equipment is dropped and duplicates collapsed`() {
        val parsed = parse(legacy() + ("equipment" to listOf("BANCO", "NADA", "BARRA", "BANCO", 7)))!!
        assertEquals(listOf(ExerciseEquipment.BARRA, ExerciseEquipment.BANCO), parsed.equipment)
    }

    @Test
    fun `malformed source yields null source but keeps the exercise`() {
        assertNull(parse(legacy() + ("source" to "wger"))!!.source)
        assertNull(parse(legacy() + ("source" to mapOf("provider" to "wger")))!!.source)
        assertEquals("Press banca", parse(legacy() + ("source" to 5))!!.name)
    }

    @Test
    fun `PESO_CORPORAL is recognised`() {
        assertEquals(ExerciseType.PESO_CORPORAL, parseExerciseType("PESO_CORPORAL"))
        assertEquals(ExerciseType.PESO_CORPORAL, parse(legacy() + ("type" to "PESO_CORPORAL"))!!.type)
    }

    @Test
    fun `unknown type falls back to OTROS`() {
        assertEquals(ExerciseType.OTROS, parseExerciseType("YOGA"))
        assertEquals(ExerciseType.OTROS, parseExerciseType(null))
        assertEquals(ExerciseType.OTROS, parse(legacy() + ("type" to "YOGA"))!!.type)
    }

    @Test
    fun `missing createdAt, name, ownerUid or createdBy drops the document`() {
        assertNull(parse(legacy(), createdAt = null))
        assertNull(parse(legacy() - "name"))
        assertNull(parse(legacy() - "ownerUid"))
        assertNull(parse(legacy() - "createdBy"))
    }

    @Test
    fun `validateExerciseInput trims, nulls blank description and orders equipment`() {
        val validated = validateExerciseInput(
            ExerciseInput(
                name = "  Remo  ",
                type = ExerciseType.MAQUINA,
                muscleGroup = "  ",
                description = "   ",
                equipment = listOf(ExerciseEquipment.BANCO, ExerciseEquipment.BARRA, ExerciseEquipment.BANCO),
            ),
        )
        assertEquals("Remo", validated.name)
        assertNull(validated.muscleGroup)
        assertNull(validated.description)
        assertEquals(listOf(ExerciseEquipment.BARRA, ExerciseEquipment.BANCO), validated.equipment)
    }

    @Test
    fun `description over 2000 chars fails with DescriptionTooLong`() {
        val input = ExerciseInput("Remo", ExerciseType.MAQUINA, null, "x".repeat(2001), emptyList())
        val error = assertThrows(ExerciseRepositoryException::class.java) { validateExerciseInput(input) }
        assertEquals(ExerciseFailure.DescriptionTooLong, error.failure)
        assertEquals("x".repeat(2000), validateExerciseInput(input.copy(description = "x".repeat(2000))).description)
    }

    @Test
    fun `toFirestoreFields writes nameLower, type and equipment names`() {
        val fields = ExerciseInput(
            "Dominadas",
            ExerciseType.PESO_CORPORAL,
            "Espalda",
            "Agarre prono.",
            listOf(ExerciseEquipment.BARRA_DOMINADAS),
        ).toFirestoreFields()
        assertEquals("Dominadas", fields["name"])
        assertEquals("dominadas", fields["nameLower"])
        assertEquals("PESO_CORPORAL", fields["type"])
        assertEquals("Espalda", fields["muscleGroup"])
        assertEquals("Agarre prono.", fields["description"])
        assertEquals(listOf("BARRA_DOMINADAS"), fields["equipment"])
    }

    @Test
    fun `sortedForCatalog sorts accented names with their base letter`() {
        val sorted = listOf(exercise("remo", "Remo"), exercise("burpee", "Burpee"), exercise("angel", "Ángel"))
            .sortedForCatalog()
        assertEquals(listOf("Ángel", "Burpee", "Remo"), sorted.map(CatalogExercise::name))
    }

    private fun wgerImage(extra: Map<String, Any?> = emptyMap()): Map<String, Any?> =
        mapOf("hash" to "a".repeat(64), "license" to "CC-BY-SA-4.0") + extra

    @Test
    fun `measure SECONDS is parsed, missing or unknown is REPS`() {
        assertEquals(SetMeasure.SECONDS, parse(legacy() + ("measure" to "SECONDS"))!!.measure)
        assertEquals(SetMeasure.REPS, parse(legacy() + ("measure" to "REPS"))!!.measure)
        assertEquals(SetMeasure.REPS, parse(legacy())!!.measure)
        assertEquals(SetMeasure.REPS, parse(legacy() + ("measure" to "MINUTES"))!!.measure)
        assertEquals(SetMeasure.REPS, parse(legacy() + ("measure" to 3))!!.measure)
    }

    @Test
    fun `image with hash and licence is parsed, bad hash and url-only legacy image are ignored`() {
        val extra = mapOf("author" to "Ana", "sourceUrl" to "https://wger.de/x")
        val image = parse(legacy() + ("image" to wgerImage(extra)))!!.image!!
        assertEquals("a".repeat(64), image.hash)
        assertEquals("Ana", image.author)
        assertEquals("CC-BY-SA-4.0", image.license)
        assertEquals("https://wger.de/x", image.sourceUrl)
        assertNull(parse(legacy() + ("image" to wgerImage()))!!.image!!.author)
        assertNull(parse(legacy() + ("image" to wgerImage()))!!.image!!.sourceUrl)
        assertNull(parseExerciseImage(wgerImage(mapOf("hash" to "A".repeat(64)))))
        assertNull(parseExerciseImage(wgerImage(mapOf("hash" to "a".repeat(63)))))
        assertNull(parseExerciseImage(wgerImage(mapOf("hash" to "g".repeat(64)))))
        assertNull(parseExerciseImage(wgerImage() - "hash"))
        assertNull(parseExerciseImage(wgerImage() - "license"))
        val urlOnly = mapOf("url" to "https://wger.de/media/a.png", "license" to "CC-BY-SA-4.0")
        assertNull(parseExerciseImage(urlOnly))
        assertNull(parseExerciseImage("x"))
        assertNull(parseExerciseImage(null))
        // La ficha se conserva aunque la imagen no valga.
        val kept = parse(legacy() + ("image" to urlOnly))
        assertEquals("Press banca", kept!!.name)
        assertNull(kept.image)
    }

    @Test
    fun `bobitos source without url parses`() {
        val source = parse(
            legacy() + (
                "source" to mapOf(
                    "provider" to "bobitos",
                    "id" to 7L,
                    "license" to "CC-BY-SA-4.0",
                    "author" to "Catálogo Bobitos",
                )
                ),
        )!!.source!!
        assertEquals("bobitos", source.provider)
        assertEquals(7L, source.sourceId)
        assertNull(source.url)
    }

    @Test
    fun `document without nameLower is kept`() {
        assertEquals("Press banca", parse(legacy() - "nameLower")!!.name)
    }

    @Test
    fun `toFirestoreFields writes measure`() {
        val base = ExerciseInput("Plancha", ExerciseType.PESO_CORPORAL, null, null, emptyList())
        assertEquals("REPS", base.toFirestoreFields()["measure"])
        val fields = base.copy(measure = SetMeasure.SECONDS).toFirestoreFields()
        assertEquals("SECONDS", fields["measure"])
        assertEquals(false, fields.containsKey("image"))
        assertEquals(false, fields.containsKey("source"))
    }

    @Test
    fun `validateExerciseInput keeps SECONDS for strength types and forces REPS otherwise`() {
        val seconds = ExerciseInput("Plancha", ExerciseType.PESO_CORPORAL, null, null, emptyList(), SetMeasure.SECONDS)
        assertEquals(SetMeasure.SECONDS, validateExerciseInput(seconds).measure)
        assertEquals(SetMeasure.REPS, validateExerciseInput(seconds.copy(type = ExerciseType.CARDIO)).measure)
        assertEquals(SetMeasure.REPS, validateExerciseInput(seconds.copy(type = ExerciseType.OTROS)).measure)
    }
}
