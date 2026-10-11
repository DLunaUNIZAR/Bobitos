package com.dlunaunizar.bobitos.core.model

import java.text.Normalizer

/**
 * Ficha de ingrediente del catálogo: común (colección top-level `ingredients`, solo la edita el admin)
 * o personal del usuario (`personal = true`). Es distinta del [Ingredient] embebido en una receta
 * (texto libre): aquí cada ingrediente es una entidad con identidad estable. El `id` es el [slug] de
 * su nombre, para deduplicar y poder resolverlo por nombre.
 */
data class CatalogIngredient(
    val id: String,
    val name: String,
    val category: String? = null,
    val defaultUnit: String? = null,
    val personal: Boolean = false,
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

// Palabras vacías de la clave tolerante; la misma lista que `nearDuplicateKey` en scripts/catalog.
private val INGREDIENT_STOP_WORDS = setOf(
    "a", "al", "con", "de", "del", "e", "el", "en", "la", "las", "lo", "los", "o", "para", "por", "u", "un", "una", "y",
)

/**
 * Clave tolerante de un nombre de ingrediente: [slug] sin palabras vacías, con cada palabra en singular
 * aproximado (-s, -e final, -z → -c) y en orden alfabético. «Limones» y «limón» comparten clave, y también
 * «Nuez» y «Nueces». Duplica `ingredientKey` de scripts/catalog/ingredients.mjs; las dos las vigila
 * tests/fixtures/ingredient-keys.json.
 */
fun ingredientKey(name: String): String = slug(name)
    .split('-')
    .filter { it.isNotEmpty() && it !in INGREDIENT_STOP_WORDS }
    .map { word ->
        var w = word
        if (w.length > 3 && w.endsWith("s") && !w.endsWith("ss")) w = w.dropLast(1)
        if (w.length > 3 && w.endsWith("e")) w = w.dropLast(1)
        if (w.endsWith("z")) w = w.dropLast(1) + "c"
        w
    }
    .sorted()
    .joinToString(" ")
