package com.dlunaunizar.bobitos.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Lanza [block] como la escritura en curso de [id]: solo hay una por id, y si ya hay otra devuelve false sin escribir.
 * Cuando acaba, de cualquier forma (también si se cancela), libera el id y avisa a [onFinished] con si terminó bien;
 * una escritura cancelada nunca cuenta como éxito. [onSuccess] (con lo que devuelve el bloque) y [onFailure] van antes del aviso; [onFailure] no se
 * llama si se cancela el propio scope (la cancelación se relanza); una cancelación lanzada por el bloque sin
 * estar cancelado es un fallo normal.
 */
fun <T> CoroutineScope.launchPerIdWrite(
    ids: MutableSet<String>,
    id: String,
    onFinished: (Boolean) -> Unit = {},
    onSuccess: (T) -> Unit = {},
    onFailure: (Throwable) -> Unit = {},
    block: suspend () -> T,
): Boolean {
    if (!ids.add(id)) return false
    launch {
        val result = runCatching { block() }
        val failure = result.exceptionOrNull()
        ids.remove(id)
        if (failure != null && failure.isOwnCancellation()) {
            onFinished(false)
            throw failure
        }
        if (failure == null) onSuccess(result.getOrThrow()) else onFailure(failure)
        onFinished(failure == null)
    }
    return true
}
