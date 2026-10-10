package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus

/**
 * Cierra el editor cuando el servidor confirma (SAVED) y consume el estado. Un FAILED con el editor ya
 * cerrado se consume para que no aparezca en el siguiente editor.
 */
@Composable
fun EditorSaveEffect(status: EditorSaveStatus, editorOpen: Boolean, onClose: () -> Unit, onConsume: () -> Unit) {
    val currentClose by rememberUpdatedState(onClose)
    val currentConsume by rememberUpdatedState(onConsume)
    LaunchedEffect(status, editorOpen) {
        when {
            status == EditorSaveStatus.SAVED -> {
                currentClose()
                currentConsume()
            }
            status == EditorSaveStatus.FAILED && !editorOpen -> currentConsume()
        }
    }
}
