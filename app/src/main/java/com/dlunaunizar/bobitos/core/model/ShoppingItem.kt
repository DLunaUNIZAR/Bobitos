package com.dlunaunizar.bobitos.core.model

import java.time.Instant

enum class Supermarket {
    DIA,
    EROSKI,
    MERCADONA,
    CARREFOUR,
    ALCAMPO,
    CARNICERIA,
    PESCADERIA,
    FRUTERIA,
    PANADERIA,
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

/**
 * Mismo contenido visible en la lista: nombre, cantidad, notas, si está comprado, tienda y marca. No mira quién
 * ni cuándo lo tocó. Sirve para no pisar el cambio de otro miembro hecho mientras tanto.
 */
fun ShoppingItem.sameContentAs(other: ShoppingItem): Boolean = name == other.name &&
    quantity == other.quantity &&
    notes == other.notes &&
    purchased == other.purchased &&
    supermarket == other.supermarket &&
    brand == other.brand
