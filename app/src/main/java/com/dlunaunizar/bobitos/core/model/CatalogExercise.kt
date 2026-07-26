package com.dlunaunizar.bobitos.core.model

import java.time.Instant

// Tipo de ejercicio: determina qué parámetros se registran (fuerza → series+peso; cardio → tiempo+nivel).
enum class ExerciseType {
    MAQUINA,
    PESO_LIBRE,
    CARDIO,
    OTROS,
}

// Ficha del catálogo global de ejercicios (≈ CatalogIngredient; id = slug del nombre).
data class CatalogExercise(
    val id: String,
    val name: String,
    val type: ExerciseType,
    val muscleGroup: String? = null,
    val ownerUid: String,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
)
