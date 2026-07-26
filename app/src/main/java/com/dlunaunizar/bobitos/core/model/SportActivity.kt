package com.dlunaunizar.bobitos.core.model

import java.time.Instant
import java.time.LocalDate

// Tipo de actividad deportiva. GIMNASIO es el caso rico (rutina de ejercicios, en fases posteriores).
enum class SportType {
    PADEL,
    FUTBOL,
    CAMINATA,
    CORRER,
    PILATES,
    GIMNASIO,
    OTROS,
}

// Actividad del planificador de deporte (≈ Meal): una por fecha, con participantes y marca «hecha».
// GIMNASIO es el caso rico: puede enlazar una rutina de origen ([routineId]) y registrar los
// ejercicios realmente hechos ese día en [session] (copia editable de la rutina).
data class SportActivity(
    val id: String,
    val date: LocalDate,
    val type: SportType,
    val name: String,
    val participantIds: List<String>,
    val participantNames: List<String>,
    val done: Boolean = false,
    val routineId: String? = null,
    val session: List<RoutineExercise> = emptyList(),
    val createdBy: String,
    val createdByName: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
)
