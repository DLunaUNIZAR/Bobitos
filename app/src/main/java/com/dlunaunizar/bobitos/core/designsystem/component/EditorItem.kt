package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

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

/** Lo que muestra un editor o un detalle abierto: el elemento existente o null si es uno nuevo. */
internal class EditorSlot<T>(val item: T?)

/**
 * Ciclo común de un editor (o detalle) abierto por id: resuelve el elemento en [items] (null mientras la
 * lista carga), lo mantiene durante una recarga (ver [stickyEditorItem]) y, si ya cargó y el elemento no
 * existe (lo borró otra persona), llama a [onGone]. Devuelve null cuando no hay que mostrar nada; con
 * [id] null y [open] es un elemento nuevo.
 */
@Composable
internal fun <T : Any> rememberEditorSlot(
    open: Boolean,
    id: String?,
    items: List<T>?,
    idOf: (T) -> String,
    onGone: () -> Unit,
): EditorSlot<T>? {
    val loaded = items != null
    val item = rememberEditorItem(id, id?.let { key -> items?.firstOrNull { idOf(it) == key } }, loaded)
    val gone = open && id != null && item == null
    val currentOnGone by rememberUpdatedState(onGone)
    LaunchedEffect(gone, loaded) {
        if (gone && loaded) currentOnGone()
    }
    return if (open && !gone) EditorSlot(item) else null
}
