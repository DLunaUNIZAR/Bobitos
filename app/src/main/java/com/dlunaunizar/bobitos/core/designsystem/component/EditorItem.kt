package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Elemento que muestra un editor de edición. Al girar el móvil el ViewModel vuelve a observar la lista y
 * pasa por «cargando»: sin esto el elemento dejaría de resolverse, el editor saldría de la composición y
 * el borrador se perdería. Mientras la lista recarga ([loaded] = false) se mantiene el último elemento
 * conocido; cuando ya cargó y el elemento no está (lo borró otra persona), se descarta.
 */
internal fun <T> stickyEditorItem(item: T?, last: T?, loaded: Boolean): T? = item ?: last.takeUnless { loaded }

private class LastItem<T> {
    var value: T? = null
}

@Composable
internal fun <T : Any> rememberEditorItem(id: String?, item: T?, loaded: Boolean): T? {
    val last = remember(id) { LastItem<T>() }
    val shown = stickyEditorItem(item, last.value, loaded)
    last.value = shown
    return shown
}
