@file:Suppress("MatchingDeclarationName")

package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing

/** Lugar de una fila dentro de su grupo: define qué esquinas son grandes. */
enum class RowPosition { SINGLE, FIRST, MIDDLE, LAST }

fun rowPosition(index: Int, count: Int): RowPosition = when {
    count <= 1 -> RowPosition.SINGLE
    index == 0 -> RowPosition.FIRST
    index == count - 1 -> RowPosition.LAST
    else -> RowPosition.MIDDLE
}

private val OuterCorner = 16.dp
private val InnerCorner = 4.dp
private val RowGap = 2.dp

/**
 * Fila de una lista agrupada: fondo de tono ([tint] o `surfaceContainer`), esquinas de 16 dp en los
 * extremos del grupo y de 4 dp por dentro, 2 dp de hueco debajo (salvo la última) y sin divisores.
 * Con [onClick] es un `Surface(onClick)`; mide al menos 48 dp.
 */
@Composable
fun GroupedRow(
    position: RowPosition,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val top = if (position == RowPosition.SINGLE || position == RowPosition.FIRST) OuterCorner else InnerCorner
    val bottom = if (position == RowPosition.SINGLE || position == RowPosition.LAST) OuterCorner else InnerCorner
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    val gap = if (position == RowPosition.FIRST || position == RowPosition.MIDDLE) RowGap else 0.dp
    val color = tint ?: MaterialTheme.colorScheme.surfaceContainer
    val outer = modifier.fillMaxWidth().padding(bottom = gap)
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = outer, shape = shape, color = color, content = body)
    } else {
        Surface(modifier = outer, shape = shape, color = color, content = body)
    }
}
