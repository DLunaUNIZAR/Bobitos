package com.dlunaunizar.bobitos.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Relanza la cancelación solo si quien la recibe está cancelado; si la lanzó otro (un repositorio), vuelve:
 * es un fallo normal.
 */
suspend fun Throwable.rethrowIfCancelled() {
    if (this is CancellationException) currentCoroutineContext().ensureActive()
}
