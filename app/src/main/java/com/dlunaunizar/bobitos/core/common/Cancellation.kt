package com.dlunaunizar.bobitos.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Relanza la cancelación solo si quien la recibe está cancelado; si la lanzó otro (un repositorio), vuelve:
 * es un fallo normal.
 */
suspend fun Throwable.rethrowIfCancelled() {
    if (isOwnCancellation()) throw this
}

/** Es una cancelación y quien la recibe también está cancelado; si no, es un fallo normal. */
suspend fun Throwable.isOwnCancellation(): Boolean =
    this is CancellationException && !currentCoroutineContext().isActive
