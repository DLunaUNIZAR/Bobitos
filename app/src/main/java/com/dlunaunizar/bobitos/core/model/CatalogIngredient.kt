package com.dlunaunizar.bobitos.core.model

import java.text.Normalizer
import java.time.Instant

/**
 * Ficha de ingrediente del catálogo global compartido por todos los usuarios (colección top-level
 * `ingredients`). Es distinta del [Ingredient] embebido en una receta (texto libre): aquí cada
 * ingrediente es una entidad con identidad estable. El `id` es el [slug] de su nombre, para
 * deduplicar y poder resolverlo por nombre. La personalización por usuario (supermercado/marca por
 * defecto) vive aparte, en las preferencias del usuario.
 */
data class CatalogIngredient(
    val id: String,
    val name: String,
    val category: String? = null,
    val defaultUnit: String? = null,
    val ownerUid: String,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Instant,
    val updatedBy: String,
    val updatedAt: Instant,
)

/**
 * Normaliza un nombre de ingrediente a un identificador estable: minúsculas, sin acentos, y cualquier
 * secuencia no alfanumérica convertida en un solo `-` (sin `-` al principio ni al final). Así «Tomate
 * frito» y «tomate  frito» comparten id y se evitan duplicados. Devuelve "" si no queda nada.
 */
fun slug(name: String): String {
    val decomposed = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
    return decomposed
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
}
