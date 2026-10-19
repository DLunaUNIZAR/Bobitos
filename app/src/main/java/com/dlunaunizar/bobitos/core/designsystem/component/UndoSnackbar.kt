package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UndoOffer
import com.dlunaunizar.bobitos.core.common.UndoOfferControls
import com.dlunaunizar.bobitos.core.common.Undoable
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
 * Muestra el aviso «Deshacer» de la primera de [offers]: la consume antes de mostrarla y llama a [onUndo] solo si el
 * usuario pulsa la acción. Una oferta no se muestra dos veces. Los avisos se encolan en el host, uno tras otro. Sin
 * host consume igual y no muestra nada. Se llama dentro del `content` del scaffold, que provee [LocalSnackbarHostState].
 */
@Composable
fun <T> UndoOfferEffect(
    offers: List<UndoOffer<T>>,
    controls: UndoOfferControls,
    @StringRes deletedMessage: Int? = null,
    @StringRes completedMessage: Int? = null,
    onUndo: (Undoable<T>) -> Unit,
) {
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val shown = remember { mutableSetOf<Long>() }
    val first = offers.firstOrNull()
    LaunchedEffect(first?.serial) {
        if (first != null && shown.add(first.serial)) {
            controls.consumeOffer(first.serial)
            val undoable = first.undoable
            val message = when (undoable) {
                is Undoable.Created -> resources.getString(R.string.created_undo_message, undoable.item.name)
                is Undoable.Deleted -> resources.getString(checkNotNull(deletedMessage))
                is Undoable.Completed -> resources.getString(checkNotNull(completedMessage))
            }
            scope.launchUndo(snackbar, message, resources.getString(R.string.undo)) { onUndo(undoable) }
        }
    }
}
