package com.dlunaunizar.bobitos.core.navigation

/**
 * Recuerda qué módulo se acaba de abrir desde una entrada de «Hoy» para que llegue con el día de hoy enfocado, y no con
 * el que tenía guardado al restaurar su estado. Solo vale para esa llegada: [take] lo gasta.
 */
internal class TodayFocus {
    private var pending: String? = null

    /** Se llama justo antes de navegar a [destination] desde «Hoy». */
    fun request(destination: BobitosDestination) {
        pending = destination.route
    }

    /** True una sola vez por cada [request] cuando [destination] es el que se pidió. */
    fun take(destination: BobitosDestination): Boolean {
        if (pending != destination.route) return false
        pending = null
        return true
    }
}
