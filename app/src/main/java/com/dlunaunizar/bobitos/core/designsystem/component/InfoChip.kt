package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Etiqueta informativa con aspecto de chip pero sin acción: no es clicable ni se anuncia como botón
 * desactivado (a diferencia de `AssistChip(enabled = false)`), así TalkBack solo lee su texto.
 * El [icon] opcional es decorativo. [containerColor] es por defecto un tinte del 12 % de [contentColor].
 */
@Composable
fun InfoChip(
    label: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = contentColor.copy(alpha = TONAL_ALPHA),
    icon: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon?.invoke()
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private const val TONAL_ALPHA = 0.12f
