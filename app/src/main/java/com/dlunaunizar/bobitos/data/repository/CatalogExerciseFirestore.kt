package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseEquipment
import com.dlunaunizar.bobitos.core.model.ExerciseImage
import com.dlunaunizar.bobitos.core.model.ExerciseInput
import com.dlunaunizar.bobitos.core.model.ExerciseSource
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.MAX_EXERCISE_DESCRIPTION_LENGTH
import com.dlunaunizar.bobitos.core.model.MAX_EXERCISE_MUSCLE_LENGTH
import com.dlunaunizar.bobitos.core.model.MAX_EXERCISE_NAME_LENGTH
import com.dlunaunizar.bobitos.core.model.SetMeasure
import com.dlunaunizar.bobitos.core.model.recordsSets
import java.text.Collator
import java.time.Instant
import java.util.Locale

// Parseo y validación puros (sin Firebase) del catálogo de ejercicios, para poder probarlos en JVM.
// Los nombres de campo son los que escribe scripts/catalog/import-plan.mjs y admiten las reglas.

// null → la ficha se descarta (le falta un campo obligatorio). Los campos nuevos son opcionales
// (retro-compat con fichas antiguas) y un tipo desconocido cae a OTROS en vez de ocultar la ficha.
internal fun parseCatalogExercise(
    id: String,
    data: Map<String, Any?>,
    createdAt: Instant?,
    updatedAt: Instant?,
): CatalogExercise? {
    val createdBy = data["createdBy"] as? String
    val name = data["name"] as? String
    val ownerUid = data["ownerUid"] as? String
    if (createdAt == null || createdBy == null) return null
    if (name == null || ownerUid == null) return null
    return CatalogExercise(
        id = id,
        name = name,
        type = parseExerciseType(data["type"]),
        muscleGroup = data["muscleGroup"] as? String,
        description = (data["description"] as? String)?.takeIf(String::isNotBlank),
        equipment = parseExerciseEquipment(data["equipment"]),
        measure = parseSetMeasure(data["measure"]),
        image = parseExerciseImage(data["image"]),
        source = parseExerciseSource(data["source"]),
        ownerUid = ownerUid,
        createdBy = createdBy,
        createdByName = data["createdByName"] as? String ?: createdBy,
        createdAt = createdAt,
        updatedBy = data["updatedBy"] as? String ?: createdBy,
        updatedAt = updatedAt ?: createdAt,
    )
}

internal fun parseExerciseType(raw: Any?): ExerciseType =
    (raw as? String)?.let { value -> runCatching { ExerciseType.valueOf(value) }.getOrNull() } ?: ExerciseType.OTROS

// Ausente o desconocida (p. ej. una beta antigua que no la escribe) → repeticiones.
internal fun parseSetMeasure(raw: Any?): SetMeasure =
    (raw as? String)?.let { value -> runCatching { SetMeasure.valueOf(value) }.getOrNull() } ?: SetMeasure.REPS

private val SHA256_HEX = Regex("[0-9a-f]{64}")

// Exige hash (sha256 hexadecimal en minúscula) y licencia; una imagen antigua solo con url se ignora y la ficha sigue sin imagen.
internal fun parseExerciseImage(raw: Any?): ExerciseImage? {
    val map = raw as? Map<*, *> ?: return null
    val hash = map["hash"] as? String
    val license = map["license"] as? String
    if (hash == null || license == null || !SHA256_HEX.matches(hash)) return null
    return ExerciseImage(hash, map["author"] as? String, license, map["sourceUrl"] as? String)
}

// Ignora desconocidos y duplicados; devuelve en el orden canónico del enum.
internal fun parseExerciseEquipment(raw: Any?): List<ExerciseEquipment> {
    val names = (raw as? List<*>).orEmpty().filterIsInstance<String>().toSet()
    return ExerciseEquipment.entries.filter { it.name in names }
}

internal fun parseExerciseSource(raw: Any?): ExerciseSource? {
    val map = raw as? Map<*, *> ?: return null
    val provider = map["provider"] as? String
    val sourceId = (map["id"] as? Number)?.toLong()
    val license = map["license"] as? String
    if (provider == null || sourceId == null || license == null) return null
    return ExerciseSource(provider, sourceId, license, map["author"] as? String, map["url"] as? String)
}

// Recorta y valida; lanza ExerciseRepositoryException con el fallo concreto.
internal fun validateExerciseInput(input: ExerciseInput): ExerciseInput {
    val name = input.name.trim()
    val muscle = input.muscleGroup?.trim()?.takeIf(String::isNotEmpty)
    val description = input.description?.trim()?.takeIf(String::isNotEmpty)
    val failure = when {
        name.isEmpty() -> ExerciseFailure.NameRequired
        name.length > MAX_EXERCISE_NAME_LENGTH -> ExerciseFailure.NameTooLong
        muscle != null && muscle.length > MAX_EXERCISE_MUSCLE_LENGTH -> ExerciseFailure.MuscleGroupTooLong
        description != null && description.length > MAX_EXERCISE_DESCRIPTION_LENGTH ->
            ExerciseFailure.DescriptionTooLong
        else -> null
    }
    if (failure != null) throw ExerciseRepositoryException(failure)
    return input.copy(
        name = name,
        measure = if (input.type.recordsSets) input.measure else SetMeasure.REPS,
        muscleGroup = muscle,
        description = description,
        equipment = ExerciseEquipment.entries.filter { it in input.equipment },
    )
}

// Campos editables por el usuario; el repositorio añade dueño y marcas de tiempo.
internal fun ExerciseInput.toFirestoreFields(): Map<String, Any?> = mapOf(
    "name" to name,
    "nameLower" to name.lowercase(),
    "type" to type.name,
    "muscleGroup" to muscleGroup,
    "description" to description,
    "equipment" to equipment.map(ExerciseEquipment::name),
    "measure" to measure.name,
)

// Orden alfabético en español sin distinguir tildes ni mayúsculas; desempata por id.
internal fun List<CatalogExercise>.sortedForCatalog(): List<CatalogExercise> =
    sortedWith(compareBy<CatalogExercise, String>(CATALOG_COLLATOR) { it.name }.thenBy { it.id })

// Compartido entre llamadas: RuleBasedCollator.compare está sincronizado.
private val CATALOG_COLLATOR: Collator =
    Collator.getInstance(Locale.forLanguageTag("es-ES")).apply { strength = Collator.PRIMARY }
