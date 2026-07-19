package com.dlunaunizar.bobitos.core.model

import java.time.Instant

enum class Supermarket {
    DIA,
    EROSKI,
    MERCADONA,
    CARREFOUR,
    ALCAMPO,
    OTROS,
    INDIFERENTE,
}

data class ShoppingItem(
    val id: String,
    val name: String,
    val quantity: String?,
    val notes: String?,
    val purchased: Boolean,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
    val purchasedBy: String?,
    val purchasedByName: String?,
    val purchasedAt: Instant?,
    val supermarket: Supermarket? = null,
    val brand: String? = null,
)
