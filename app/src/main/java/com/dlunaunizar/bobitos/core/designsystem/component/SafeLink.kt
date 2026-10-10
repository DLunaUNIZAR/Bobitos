package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration

/**
 * Fábrica de enlaces para `withLink` / `pushLink`. Abren con el gestor de URI del sistema envuelto en
 * `runCatching`: en un dispositivo sin navegador no se hace nada en vez de cerrar la app. Al ser
 * `LinkAnnotation`, TalkBack los anuncia como enlaces.
 */
@Composable
fun rememberSafeLinks(): (String) -> LinkAnnotation.Url {
    val uriHandler = LocalUriHandler.current
    val color = MaterialTheme.colorScheme.primary
    return remember(uriHandler, color) {
        val style = TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))
        val link: (String) -> LinkAnnotation.Url = { url ->
            LinkAnnotation.Url(url, style) { runCatching { uriHandler.openUri(url) } }
        }
        link
    }
}
