package com.dlunaunizar.bobitos.core.model

import java.time.Instant
import java.time.LocalDate

enum class MealSlot {
    DESAYUNO,
    COMIDA,
    CENA,
}

data class Meal(
    val id: String,
    val date: LocalDate,
    val slot: MealSlot,
    val name: String,
    val participantIds: List<String>,
    val participantNames: List<String>,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
)
