package com.dlunaunizar.bobitos.core.model

import java.time.Instant

enum class RoutineVisibility { GLOBAL, PRIVATE }

// Rutina reutilizable del catálogo (≈ Recipe): embebe una lista de ejercicios con sus parámetros.
data class Routine(
    val id: String,
    val ownerUid: String,
    val visibility: RoutineVisibility,
    val title: String,
    val description: String? = null,
    val exercises: List<RoutineExercise>? = null,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
)
