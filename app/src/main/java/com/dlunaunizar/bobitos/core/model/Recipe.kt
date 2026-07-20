package com.dlunaunizar.bobitos.core.model

import java.time.Instant

enum class RecipeVisibility {
    GLOBAL,
    PRIVATE,
}

data class Recipe(
    val id: String,
    val ownerUid: String,
    val visibility: RecipeVisibility,
    val title: String,
    val description: String?,
    val category: String?,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
)
