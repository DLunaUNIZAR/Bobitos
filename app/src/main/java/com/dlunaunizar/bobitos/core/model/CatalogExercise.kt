package com.dlunaunizar.bobitos.core.model

import java.time.Instant

// Tipo de ejercicio: determina qué parámetros se registran (fuerza → series+peso; cardio → tiempo+nivel).
// PESO_CORPORAL son series de repeticiones con lastre opcional (dominadas, fondos…).
enum class ExerciseType {
    MAQUINA,
    PESO_LIBRE,
    PESO_CORPORAL,
    CARDIO,
    OTROS,
}

// Material necesario. El orden es el canónico (el mismo que EQUIPMENT en scripts/catalog).
enum class ExerciseEquipment {
    BARRA,
    BARRA_Z,
    MANCUERNAS,
    KETTLEBELL,
    DISCO,
    POLEA,
    MAQUINA,
    BANCO,
    BANCO_INCLINADO,
    BARRA_DOMINADAS,
    ESTERILLA,
    FITBALL,
    BANDA_ELASTICA,
}

// En qué se mide cada serie del ejercicio: repeticiones (por defecto) o segundos (isométricos).
enum class SetMeasure {
    REPS,
    SECONDS,
}

// Cierto si el tipo registra series (máquina/peso libre/peso corporal) frente a tiempo/nivel (cardio, otros).
val ExerciseType.recordsSets: Boolean
    get() = this == ExerciseType.MAQUINA || this == ExerciseType.PESO_LIBRE || this == ExerciseType.PESO_CORPORAL

// Imagen de la ficha, enlazada desde wger.de con su autoría y licencia (no se copia ni se sube).
data class ExerciseImage(val url: String, val author: String?, val license: String)

// Procedencia de una ficha importada (p. ej. wger) para atribuir autor y licencia.
data class ExerciseSource(
    val provider: String,
    val sourceId: Long,
    val license: String,
    val author: String? = null,
    val url: String? = null,
)

// Longitudes máximas (editor y validación del repositorio; coinciden con las reglas).
const val MAX_EXERCISE_NAME_LENGTH = 120
const val MAX_EXERCISE_MUSCLE_LENGTH = 60
const val MAX_EXERCISE_DESCRIPTION_LENGTH = 2000

// Datos editables de una ficha (lo que escribe el editor); el repositorio los valida y persiste.
data class ExerciseInput(
    val name: String,
    val type: ExerciseType,
    val muscleGroup: String?,
    val description: String?,
    val equipment: List<ExerciseEquipment>,
    val measure: SetMeasure = SetMeasure.REPS,
)

// Ficha del catálogo global de ejercicios (≈ CatalogIngredient; id = slug del nombre).
data class CatalogExercise(
    val id: String,
    val name: String,
    val type: ExerciseType,
    val muscleGroup: String? = null,
    val description: String? = null,
    val equipment: List<ExerciseEquipment> = emptyList(),
    val measure: SetMeasure = SetMeasure.REPS,
    val image: ExerciseImage? = null,
    val source: ExerciseSource? = null,
    val ownerUid: String,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
)
