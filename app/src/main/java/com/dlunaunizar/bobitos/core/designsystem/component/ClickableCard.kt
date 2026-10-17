package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Tarjeta que se puede pulsar solo si [onClick] no es nulo. Sin él es una `Card` normal:
 * `Card(onClick, enabled = false)` cambiaría su color y la anunciaría como desactivada.
 */
@Composable
fun ClickableCard(onClick: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, content = content)
    } else {
        Card(modifier = modifier, content = content)
    }
}
