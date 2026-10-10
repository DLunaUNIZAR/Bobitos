package com.dlunaunizar.bobitos.core.common

import java.text.Normalizer

private val DIACRITICS = Regex("\\p{Mn}+")

// Minúsculas y sin tildes (ni diéresis), para buscar «jalon» y encontrar «Jalón».
fun String.foldForSearch(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(DIACRITICS, "")
    .lowercase()

// Cada palabra de la consulta (sin tildes) tiene que aparecer en algún campo; el orden no importa.
// Una consulta en blanco coincide con todo.
fun matchesQuery(query: String, vararg fields: String?): Boolean {
    val words = query.foldForSearch().split(' ', '\t', '\n').filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val haystack = fields.filterNotNull().map { it.foldForSearch() }
    return words.all { word -> haystack.any { it.contains(word) } }
}
