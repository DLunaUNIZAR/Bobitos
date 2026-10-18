package com.dlunaunizar.bobitos.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Lanza [block] como la escritura en curso de [id]: solo hay una por id, y si ya hay otra devuelve false sin escribir.
 * Cuando acaba, de cualquier forma (también si se cancela), libera el id y avisa a [onFinished] con si terminó bien;
 * una escritura cancelada nunca cuenta como éxito. [onSuccess] y [onFailure] van antes del aviso; [onFailure] no se
 * llama al cancelar, y la cancelación se relanza.
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
        when {
            failure == null -> onSuccess()
            failure !is CancellationException -> onFailure(failure)
        }
        onFinished(failure == null)
        if (failure is CancellationException) throw failure
    }
    return true
}
