@file:Suppress("MatchingDeclarationName")

package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

// El archivo se llama como la función pública que usan las pantallas, no como el estado.
/** Acciones que la pantalla abierta pone en su barra superior; las lee el marco que dibuja la barra. */
class TopBarActionsState {
    var actions: (@Composable RowScope.() -> Unit)? by mutableStateOf(null)
}

/** El estado lo publica el marco (`WorkspaceScaffold`); sin marco es null y [TopBarActions] no hace nada. */
val LocalTopBarActions = staticCompositionLocalOf<TopBarActionsState?> { null }

/**
 * Registra [content] como acciones de la barra superior del marco mientras esta llamada esté en
 * composición, antes de los ajustes y el perfil. Al salir de composición las retira.
 */
@Composable
fun TopBarActions(content: @Composable RowScope.() -> Unit) {
    val state = LocalTopBarActions.current ?: return
    DisposableEffect(state, content) {
        state.actions = content
        onDispose { if (state.actions === content) state.actions = null }
    }
}
