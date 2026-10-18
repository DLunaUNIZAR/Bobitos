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

/**
 * Selección tras tocar [id] en un filtro múltiple. «Todas» es siempre la selección con todos los [known]: tocar a
 * alguien desde «Todas» lo quita, y desmarcar al último vuelve a «Todas» (no hay «Ninguna»).
 */
fun toggledSelection(selected: Set<String>, known: Set<String>, id: String): Set<String> {
    val base = selected.ifEmpty { known }
    return (if (id in base) base - id else base + id).ifEmpty { known }
}

/**
 * Selección cuando los ids conocidos pasan a ser [current]. Si era «Todas» sigue siéndolo, con los nuevos dentro; si
 * era parcial se quitan los que ya no existen (y si no queda ninguno, vuelve a «Todas»).
 */
fun reconciledSelection(selected: Set<String>, known: Set<String>, current: Set<String>): Set<String> =
    if (selected.isEmpty() || selected == known) current else (selected intersect current).ifEmpty { current }
