package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.LocalAppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.moduleTint

/** Radio de las esquinas de abajo de la franja teñida del módulo. */
internal val HeaderBandCorner = 20.dp

private val HeaderBandShape = RoundedCornerShape(bottomStart = HeaderBandCorner, bottomEnd = HeaderBandCorner)

/**
 * Cuántas [ModuleHeaderBand] fijas hay en composición bajo el marco. El marco lo publica y, mientras vale
 * cero, dibuja él el remate redondeado bajo la barra ([ModuleHeaderCap]); con una franja fija en pantalla
 * es la franja la que redondea y el marco no pinta nada, así que no hay doble redondeo ni corte. Las
 * [CollapsibleHeaderBand] no cuentan: van dentro de la barra y el remate las cierra siempre.
 */
class HeaderBandState {
    internal var count by mutableIntStateOf(0)
    val owned: Boolean get() = count > 0
}

val LocalHeaderBandState = staticCompositionLocalOf<HeaderBandState?> { null }

/**
 * Franja fija teñida con el tinte del módulo y las esquinas de abajo redondeadas, para lo que no se
 * encoge con la barra (la tira de días de Comidas y Deporte, los calendarios). Va pegada bajo la barra
 * (que lleva el mismo tinte) y, al estar en composición, avisa al marco para que no dibuje su propio
 * remate. Los filtros y la búsqueda de las listas van en [CollapsibleHeaderBand]. Sin módulo no tiene fondo.
 */
@Composable
fun ModuleHeaderBand(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    DeclareHeaderBand()
    val module = LocalAppModule.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (module != null) Modifier.clip(HeaderBandShape).background(moduleTint(module)) else Modifier),
        content = content,
    )
}

/**
 * Una pantalla cuya franja fija vive dentro de un elemento de lista la declara aquí, fuera de ella: el
 * elemento sale de composición al desplazar y, sin esto, el marco creería que ya no hay franja y añadiría su
 * remate (el contenido bajaría 20 dp). Sin efecto fuera de un marco.
 */
@Composable
fun DeclareHeaderBand() {
    val state = LocalHeaderBandState.current ?: return
    DisposableEffect(state) {
        state.count++
        onDispose { state.count-- }
    }
}

/** La franja que la pantalla abierta pone en la zona abierta de la barra; la lee el marco que dibuja la barra. */
class CollapsibleBandState {
    var content: (@Composable ColumnScope.() -> Unit)? by mutableStateOf(null)
}

/** El estado lo publican `ScreenScaffold` y `WorkspaceScaffold`; sin marco es null y [CollapsibleHeaderBand] no hace nada. */
val LocalCollapsibleBand = staticCompositionLocalOf<CollapsibleBandState?> { null }

/**
 * Registra [content] (filtros, búsqueda) como franja de la barra del marco mientras esta llamada esté en
 * composición. Va en la zona abierta, bajo el título, y se encoge con él: el marco la cierra siempre con
 * [ModuleHeaderCap]. Al salir de composición la retira. Lo que [content] lee se actualiza sin volver a registrarla.
 */
@Composable
fun CollapsibleHeaderBand(content: @Composable ColumnScope.() -> Unit) {
    val state = LocalCollapsibleBand.current ?: return
    val current by rememberUpdatedState(content)
    DisposableEffect(state) {
        val registered: @Composable ColumnScope.() -> Unit = { current() }
        state.content = registered
        onDispose { if (state.content === registered) state.content = null }
    }
}

/** Remate de la barra para las pantallas sin franja propia: la misma banda, sin contenido. */
@Composable
internal fun ModuleHeaderCap(state: HeaderBandState) {
    val module = LocalAppModule.current ?: return
    // `owned` se lee al medir, no al componer: si la franja se declara en la primera composición, el remate nunca llega
    // a ocupar sitio en ese primer fotograma.
    Column(
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val height = if (state.owned) 0 else HeaderBandCorner.roundToPx()
                val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
            .clip(HeaderBandShape)
            .background(moduleTint(module))
            .testTag("moduleHeaderCap"),
    ) {}
}
