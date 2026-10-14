package com.dlunaunizar.bobitos.feature.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

// Lleva la lista al primer elemento cuando [key] cambia de verdad. Una lista con claves se queda anclada en el
// elemento que estaba arriba aunque el filtro lo haya movido lejos. Se guarda el último valor visto (también al
// girar el móvil), así que ni la primera composición ni la recreación tras una rotación borran el desplazamiento.
@Composable
internal fun ScrollToTopOnChange(key: String, state: LazyListState) {
    var lastKey by rememberSaveable { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (key != lastKey) {
            lastKey = key
            state.scrollToItem(0)
        }
    }
}
