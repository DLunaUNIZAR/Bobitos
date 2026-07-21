package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * `SnackbarHostState` del scaffold activo, para que cualquier módulo muestre avisos con acción sin
 * duplicar el host. Lo provee `WorkspaceScaffold` (y el Recetario en su propio scaffold).
 */
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState?> { null }

/** Muestra un aviso con acción «Deshacer»; devuelve true si el usuario pulsa la acción. */
suspend fun SnackbarHostState.showUndo(message: String, actionLabel: String): Boolean =
    showSnackbar(message = message, actionLabel = actionLabel, duration = SnackbarDuration.Short) ==
        SnackbarResult.ActionPerformed

/** Lanza un aviso «Deshacer» y ejecuta [onUndo] solo si el usuario pulsa la acción. No-op sin host. */
fun CoroutineScope.launchUndo(
    snackbar: SnackbarHostState?,
    message: String,
    actionLabel: String,
    onUndo: () -> Unit,
): Job = launch {
    if (snackbar != null && snackbar.showUndo(message, actionLabel)) {
        onUndo()
    }
}
