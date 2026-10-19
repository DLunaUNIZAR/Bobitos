package com.dlunaunizar.bobitos.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lo que la pantalla puede pedir al guardado: consumir lo mostrado y soltar el estado del editor. */
interface SaveFeedbackControls {
    fun consumeError()

    fun consumeNotice()

    fun consumeCreated()

    /** Editor a IDLE y `editorError` a null. */
    fun resetEditor()

    /** Si el editor está FAILED: su error pasa a `error` y el editor vuelve a IDLE. */
    fun releaseEditorError()
}

/**
 * Dueño de las transiciones de [SaveState] dentro del estado [S] de una pantalla: [read] saca el [SaveState]
 * y [write] lo devuelve puesto. Cada [run] o [create] es una acción en curso; solo una a la vez salvo `force`.
 */
class SaveRunner<S, E, N>(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<S>,
    private val read: (S) -> SaveState<E, N>,
    private val write: S.(SaveState<E, N>) -> S,
    private val toError: (Throwable) -> E,
) : SaveFeedbackControls {
    private fun update(transform: (SaveState<E, N>) -> SaveState<E, N>) {
        state.update { it.write(transform(read(it))) }
    }

    /**
     * Lanza [action] como acción en curso: empezar o fallar no borra el aviso ni el error que otra acción dejó
     * pendientes, y acabar bien sin aviso propio tampoco. Devuelve false sin hacer nada si ya hay otra y no es [force].
     * Con [editor], mueve también el estado del editor y la acción lleva tiempo máximo; un fallo va a
     * `editorError` y no a `error`. [afterSuccess] se llama después de actualizar el estado.
     */
    fun <T> run(
        notice: N? = null,
        editor: Boolean = false,
        force: Boolean = false,
        noticeOf: (T) -> N? = { notice },
        onSuccess: S.(T) -> S = { this },
        onFailure: S.(Throwable) -> S = { this },
        errorOf: (Throwable) -> E = toError,
        afterSuccess: (T) -> Unit = {},
        action: suspend () -> T,
    ): Boolean = launchAction(editor, force, noticeOf, null, onSuccess, onFailure, errorOf, afterSuccess, action)

    /** Como [run], pero el resultado es lo creado y queda en `created` (con su `onSuccess` y `afterSuccess`). */
    fun create(
        editor: Boolean = false,
        force: Boolean = false,
        onSuccess: S.(CreatedItem) -> S = { this },
        afterSuccess: (CreatedItem) -> Unit = {},
        action: suspend () -> CreatedItem,
    ): Boolean = launchAction(
        editor = editor,
        force = force,
        noticeOf = { null },
        created = { it },
        onSuccess = onSuccess,
        onFailure = { this },
        errorOf = toError,
        afterSuccess = afterSuccess,
        action = action,
    )

    @Suppress("LongParameterList", "TooGenericExceptionCaught")
    private fun <T> launchAction(
        editor: Boolean,
        force: Boolean,
        noticeOf: (T) -> N?,
        created: ((T) -> CreatedItem)?,
        onSuccess: S.(T) -> S,
        onFailure: S.(Throwable) -> S,
        errorOf: (Throwable) -> E,
        afterSuccess: (T) -> Unit,
        action: suspend () -> T,
    ): Boolean {
        var started = false
        state.update { current ->
            val save = read(current)
            started = force || save.running == 0
            if (!started) {
                current
            } else {
                current.write(
                    save.copy(
                        running = save.running + 1,
                        editor = if (editor) EditorSaveStatus.SAVING else save.editor,
                        editorError = if (editor) null else save.editorError,
                    ),
                )
            }
        }
        if (!started) return false
        scope.launch {
            val result = try {
                Result.success(withEditorSaveTimeout(editor, action))
            } catch (e: Throwable) {
                if (e.isOwnCancellation()) {
                    update { it.copy(running = it.running - 1) }
                    throw e
                }
                Result.failure(e)
            }
            result.onSuccess { value ->
                state.update { current ->
                    val save = read(current)
                    current.write(
                        save.copy(
                            running = save.running - 1,
                            notice = noticeOf(value) ?: save.notice,
                            editor = if (editor) EditorSaveStatus.SAVED else save.editor,
                            created = created?.invoke(value) ?: save.created,
                        ),
                    ).onSuccess(value)
                }
                afterSuccess(value)
            }.onFailure { e ->
                state.update { current ->
                    val save = read(current)
                    val error = errorOf(e)
                    current.write(
                        if (editor) {
                            save.copy(
                                running = save.running - 1,
                                editor = EditorSaveStatus.FAILED,
                                editorError = error,
                            )
                        } else {
                            save.copy(running = save.running - 1, error = error)
                        },
                    ).onFailure(e)
                }
            }
        }
        return true
    }

    /**
     * Acción en segundo plano: no enciende «Guardando…» ni bloquea otras. Si acaba bien, muestra [noticeOf] de su
     * resultado (si hay); si falla, su error va a la pantalla. La cancelación del propio scope no es un fallo.
     */
    @Suppress("TooGenericExceptionCaught")
    fun <T> launchQuiet(noticeOf: (T) -> N? = { null }, action: suspend () -> T) {
        scope.launch {
            val value = try {
                action()
            } catch (e: Throwable) {
                if (e.isOwnCancellation()) throw e
                showError(toError(e))
                return@launch
            }
            noticeOf(value)?.let(::showNotice)
        }
    }

    /** Error del formulario abierto, sin tocar nada más. */
    fun showEditorError(error: E) = update { it.copy(editor = EditorSaveStatus.FAILED, editorError = error) }

    fun showError(error: E) = update { it.copy(error = error) }

    fun showNotice(notice: N) = update { it.copy(notice = notice) }

    /** Cambia el error y el aviso de la pantalla por [notice], de una sola vez. */
    fun replaceNotice(notice: N) = update { it.copy(error = null, notice = notice) }

    /** Borra el error y el aviso de la pantalla. */
    fun clearFeedback() = update { it.copy(error = null, notice = null) }

    override fun consumeError() = update { it.copy(error = null) }

    override fun consumeNotice() = update { it.copy(notice = null) }

    override fun consumeCreated() = update { it.copy(created = null) }

    override fun resetEditor() = update { it.copy(editor = EditorSaveStatus.IDLE, editorError = null) }

    override fun releaseEditorError() = update {
        if (it.editor == EditorSaveStatus.FAILED) {
            it.copy(editor = EditorSaveStatus.IDLE, editorError = null, error = it.editorError)
        } else {
            it
        }
    }
}
