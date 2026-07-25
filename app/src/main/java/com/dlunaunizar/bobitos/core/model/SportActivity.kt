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
data class SportActivity(
    val id: String,
    val date: LocalDate,
    val type: SportType,
    val name: String,
    val participantIds: List<String>,
    val participantNames: List<String>,
    val done: Boolean = false,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
)
