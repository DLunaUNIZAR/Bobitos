package com.dlunaunizar.bobitos.feature.calendar

/** Lo que dice el valor de un filtro de varias opciones: todas, una por su nombre o un número. */
sealed interface MultiFilterValue {
    data object All : MultiFilterValue

    data class One(val name: String) : MultiFilterValue

    data class Some(val count: Int) : MultiFilterValue
}

/**
 * Valor de un filtro múltiple a partir de lo elegido. [all] son pares de id y nombre. Sin ninguna elegida o con todas
 * es [MultiFilterValue.All]: no hay «Ninguna», elegir «Todas» vuelve a todas.
 */
fun multiFilterValue(selectedIds: Set<String>, all: List<Pair<String, String>>): MultiFilterValue {
    val chosen = all.filter { (id, _) -> id in selectedIds }
    return when {
        chosen.isEmpty() || chosen.size == all.size -> MultiFilterValue.All
        chosen.size == 1 -> MultiFilterValue.One(chosen.single().second)
        else -> MultiFilterValue.Some(chosen.size)
    }
}
