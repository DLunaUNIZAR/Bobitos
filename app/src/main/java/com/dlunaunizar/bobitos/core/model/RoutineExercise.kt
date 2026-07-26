package com.dlunaunizar.bobitos.core.model

// Una serie de un ejercicio de fuerza: repeticiones y peso (ambos opcionales).
data class ExerciseSet(val reps: Int? = null, val weight: Double? = null)

// Ejercicio dentro de una rutina o de una sesión de gimnasio (parámetros según [type]):
// fuerza (máquina/peso libre) usa [sets]; cardio usa [durationMinutes] + [level].
data class RoutineExercise(
    val name: String,
    val exerciseId: String? = null, // enlace opcional a CatalogExercise (slug)
    val type: ExerciseType,
    val sets: List<ExerciseSet> = emptyList(),
    val durationMinutes: Int? = null,
    val level: String? = null,
    val notes: String? = null,
)
