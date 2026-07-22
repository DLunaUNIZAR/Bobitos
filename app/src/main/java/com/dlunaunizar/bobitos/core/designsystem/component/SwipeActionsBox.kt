package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing

/**
 * Envuelve [content] con gestos de deslizar. Al superar el umbral ejecuta la acción y **vuelve a su
 * sitio** (no queda descartado): la lista se actualiza sola cuando cambian los datos, evitando
 * estados «colgados». Cada dirección se habilita solo si se pasa su callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActionsBox(
    startAction: SwipeAction?,
    endAction: SwipeAction?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> startAction?.onSwipe?.invoke()
                SwipeToDismissBoxValue.EndToStart -> endAction?.onSwipe?.invoke()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = startAction != null,
        enableDismissFromEndToStart = endAction != null,
        backgroundContent = {
            val action = when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> startAction
                SwipeToDismissBoxValue.EndToStart -> endAction
                SwipeToDismissBoxValue.Settled -> null
            }
            SwipeBackground(action, alignEnd = state.dismissDirection == SwipeToDismissBoxValue.EndToStart)
        },
        content = { content() },
    )
}

/** Acción de deslizar: icono, color de fondo y qué hacer al confirmarla. */
data class SwipeAction(val icon: ImageVector, val background: Color, val onSwipe: () -> Unit)

@Composable
private fun SwipeBackground(action: SwipeAction?, alignEnd: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (action != null) {
            Icon(action.icon, contentDescription = null, tint = action.background)
        }
    }
}
