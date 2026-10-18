package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.dlunaunizar.bobitos.core.designsystem.pressScale
import com.dlunaunizar.bobitos.core.designsystem.rememberReduceMotion

/**
 * Tarjeta que se puede pulsar solo si [onClick] no es nulo. Sin él es una `Card` normal:
 * `Card(onClick, enabled = false)` cambiaría su color y la anunciaría como desactivada.
 */
@Composable
fun ClickableCard(onClick: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        Card(
            onClick = onClick,
            modifier = modifier.pressScale(interactionSource, rememberReduceMotion()),
            interactionSource = interactionSource,
            content = content,
        )
    } else {
        Card(modifier = modifier, content = content)
    }
}
