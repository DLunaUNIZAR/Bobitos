package com.dlunaunizar.bobitos.feature.common

import com.dlunaunizar.bobitos.core.model.PantryEntry

// Puente entre la Compra y la Despensa (spec 2026-10-13-despensa-design, §2 y §3).

/**
 * La entrada de despensa de [name] si es un ingrediente reconocido (común o personal, por clave tolerante), con su
 * nombre canónico y su categoría; `null` si no lo es (solo los ingredientes entran solos en la despensa).
 */
internal fun pantryEntryFor(name: String, data: IngredientShoppingData): PantryEntry? =
    data.findIngredient(name)?.let { PantryEntry(it.name, it.category) }
