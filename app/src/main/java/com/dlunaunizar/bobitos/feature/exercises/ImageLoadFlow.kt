package com.dlunaunizar.bobitos.feature.exercises

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Una carga de imagen: emite null al empezar (el bloque desaparece) y luego el resultado; nunca el valor de una carga anterior.
internal fun <T : Any> imageLoadFlow(load: suspend () -> T?): Flow<T?> = flow {
    emit(null)
    emit(load())
}
