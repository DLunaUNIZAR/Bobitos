package com.dlunaunizar.bobitos.core.common

import kotlinx.coroutines.withTimeoutOrNull

/** Estado del guardado del editor abierto; solo los guardados del propio editor lo mueven. */
enum class EditorSaveStatus { IDLE, SAVING, SAVED, FAILED }

fun EditorSaveStatus.started(editor: Boolean): EditorSaveStatus = if (editor) EditorSaveStatus.SAVING else this

fun EditorSaveStatus.succeeded(editor: Boolean): EditorSaveStatus = if (editor) EditorSaveStatus.SAVED else this

fun EditorSaveStatus.failed(editor: Boolean): EditorSaveStatus = if (editor) EditorSaveStatus.FAILED else this

const val EDITOR_SAVE_TIMEOUT_MILLIS = 20_000L

private const val SAVE_TIMEOUT_MESSAGE =
    "No hay respuesta del servidor. Puede que se guarde al recuperar la conexión: compruébalo antes de repetir"

/** Mismo texto que `R.string.write_timeout` (un test lo comprueba). */
class SaveTimeoutException : Exception(SAVE_TIMEOUT_MESSAGE)

/**
 * Ejecuta [block] con un tiempo máximo. El tiempo agotado se detecta aunque el bloque convierta la
 * cancelación en otra excepción (los repositorios hacen `catch (Throwable)`); una cancelación externa
 * se propaga como cancelación.
 */
suspend fun <T> withSaveTimeout(timeoutMillis: Long = EDITOR_SAVE_TIMEOUT_MILLIS, block: suspend () -> T): T {
    val result = withTimeoutOrNull(timeoutMillis) { runCatching { block() } } ?: throw SaveTimeoutException()
    return result.getOrThrow()
}
