package com.dlunaunizar.bobitos.core.common

import java.text.Normalizer

private val WORD_SEPARATORS = Regex("[\\s\\u00A0\\u2007\\u202F]+")
private val DIACRITICS = Regex("\\p{M}+")

// Minúsculas y sin marcas diacríticas (tildes, diéresis, marcas envolventes; igual que slug), para buscar «jalon» y encontrar «Jalón».
fun String.foldForSearch(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(DIACRITICS, "")
    .lowercase()

// Cada palabra de la consulta (sin tildes) tiene que aparecer en algún campo; el orden no importa.
// Una consulta en blanco coincide con todo.
fun matchesQuery(query: String, vararg fields: String?): Boolean = prepareQuery(query).matches(*fields)

// Consulta ya plegada y troceada, para filtrar muchos ítems sin repetir ese trabajo por cada uno.
class SearchQuery internal constructor(private val words: List<String>) {
    fun matches(vararg fields: String?): Boolean {
        if (words.isEmpty()) return true
        val haystack = fields.filterNotNull().map { it.foldForSearch() }
        return words.all { word -> haystack.any { it.contains(word) } }
    }
}

fun prepareQuery(query: String): SearchQuery =
    SearchQuery(query.foldForSearch().split(WORD_SEPARATORS).filter { it.isNotEmpty() })
