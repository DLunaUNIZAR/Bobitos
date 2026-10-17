package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.CreatedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * `SnackbarHostState` del scaffold activo, para que cualquier módulo muestre avisos con acción sin
 * duplicar el host. Lo publican `ScreenScaffold` y `ModuleScaffold` (y `WorkspaceScaffold` fuera de los módulos).
 */
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState?> { null }

/** Muestra un aviso con acción «Deshacer»; devuelve true si el usuario pulsa la acción. */
suspend fun SnackbarHostState.showUndo(message: String, actionLabel: String): Boolean =
    showSnackbar(message = message, actionLabel = actionLabel, duration = SnackbarDuration.Long) ==
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

/**
 * Avisa «Has añadido «nombre»» con «Deshacer» cuando [created] trae un alta recién hecha. Se llama dentro del `content`
 * del scaffold de la pantalla, que provee [LocalSnackbarHostState]. Consume el alta antes de mostrar el aviso y solo
 * llama a [onUndo] si el usuario pulsa la acción.
 */
@Composable
fun CreatedUndoEffect(created: CreatedItem?, onConsumed: () -> Unit, onUndo: (CreatedItem) -> Unit) {
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val actionLabel = stringResource(R.string.undo)
    val message = created?.let { stringResource(R.string.created_undo_message, it.name) }
    LaunchedEffect(created) {
        if (created != null && message != null) {
            onConsumed()
            scope.launchUndo(snackbar, message, actionLabel) { onUndo(created) }
        }
    }
}
