@file:Suppress("MatchingDeclarationName")

package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import kotlinx.coroutines.launch

/** Resultado de una acción para el snackbar de la pantalla. */
data class Feedback<M>(val message: M, val isError: Boolean)

/**
 * Qué avisar en el snackbar. Un error de guardado con la hoja abierta ya se ve dentro de la hoja, así que
 * no sale también detrás; si no, el error gana al aviso.
 */
fun <M> feedbackFor(error: M?, notice: M?, editorSave: EditorSaveStatus, editorOpen: Boolean): Feedback<M>? = when {
    error != null && editorOpen && editorSave == EditorSaveStatus.FAILED -> null
    error != null -> Feedback(error, isError = true)
    notice != null -> Feedback(notice, isError = false)
    else -> null
}

/** Barra de «guardando» del andamio: solo cuando el guardado no es el de un editor abierto (ese ya muestra el suyo). */
fun savingOutsideEditor(saving: Boolean, editorOpen: Boolean): Boolean = saving && !editorOpen

/** Cerrar un editor tras un guardado fallido descarta ese error: ya se veía dentro del editor. */
fun EditorSaveStatus.discardFailure(onClear: () -> Unit) {
    if (this == EditorSaveStatus.FAILED) onClear()
}

/**
 * Muestra [feedback] en el snackbar del andamio y llama enseguida a [onShown] para limpiar el estado.
 * El aviso se lanza desde un scope propio para que limpiar el estado no lo cierre. Se cierra solo.
 */
@Composable
fun FeedbackSnackbarEffect(feedback: Feedback<String>?, onShown: () -> Unit) {
    val host = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val currentOnShown = rememberUpdatedState(onShown)
    LaunchedEffect(feedback) {
        if (feedback != null) {
            currentOnShown.value()
            scope.launch {
                host?.showSnackbar(
                    message = feedback.message,
                    duration = if (feedback.isError) SnackbarDuration.Long else SnackbarDuration.Short,
                )
            }
        }
    }
}

/** [feedbackFor] y [FeedbackSnackbarEffect] juntos: [text] resuelve el mensaje de la pantalla a texto. */
@Composable
fun <M> FeedbackSnackbarEffect(
    error: M?,
    notice: M?,
    editorSave: EditorSaveStatus,
    editorOpen: Boolean,
    onShown: () -> Unit,
    text: @Composable (M) -> String,
) {
    val feedback = feedbackFor(error, notice, editorSave, editorOpen)
    FeedbackSnackbarEffect(feedback?.let { Feedback(text(it.message), it.isError) }, onShown)
}
