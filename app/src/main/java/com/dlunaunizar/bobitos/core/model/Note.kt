package com.dlunaunizar.bobitos.core.model

import java.time.Instant

// Nota o lista libre compartida del espacio (wifi, recados, ideas): lo que no es tarea ni compra.
data class Note(
    val id: String,
    val title: String,
    val body: String?,
    val pinned: Boolean,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
)
