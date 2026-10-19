@file:Suppress("MatchingDeclarationName")

package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch

/** Resultado de una acción para el snackbar de la pantalla. */
data class Feedback<M>(val message: M, val isError: Boolean)

/** Barra de «guardando» del andamio: solo cuando el guardado no es el de un editor abierto (ese ya muestra el suyo). */
fun savingOutsideEditor(saving: Boolean, editorOpen: Boolean): Boolean = saving && !editorOpen

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
