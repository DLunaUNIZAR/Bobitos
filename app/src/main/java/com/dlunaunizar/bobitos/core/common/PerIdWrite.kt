package com.dlunaunizar.bobitos.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Lanza [block] como la escritura en curso de [id]: solo hay una por id, y si ya hay otra devuelve false sin escribir.
 * Cuando acaba, de cualquier forma (también si se cancela), libera el id y avisa a [onFinished] con si terminó bien;
 * una escritura cancelada nunca cuenta como éxito. [onSuccess] y [onFailure] van antes del aviso; [onFailure] no se
 * llama si se cancela el propio scope (la cancelación se relanza); una cancelación lanzada por el bloque sin
 * estar cancelado es un fallo normal.
 */
fun CoroutineScope.launchPerIdWrite(
    ids: MutableSet<String>,
    id: String,
    onFinished: (Boolean) -> Unit = {},
    onSuccess: () -> Unit = {},
    onFailure: (Throwable) -> Unit = {},
    block: suspend () -> Unit,
): Boolean {
    if (!ids.add(id)) return false
    launch {
        val failure = runCatching { block() }.exceptionOrNull()
        ids.remove(id)
        try {
            failure?.rethrowIfCancelled()
        } catch (cancelled: CancellationException) {
            onFinished(false)
            throw cancelled
        }
        if (failure == null) onSuccess() else onFailure(failure)
        onFinished(failure == null)
    }
    return true
}
