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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.LocalAppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.moduleTint

/** Radio de las esquinas de abajo de la franja teñida del módulo. */
internal val HeaderBandCorner = 20.dp

private val HeaderBandShape = RoundedCornerShape(bottomStart = HeaderBandCorner, bottomEnd = HeaderBandCorner)

/**
 * Cuántas [ModuleHeaderBand] hay en composición bajo el marco. El marco lo publica y, mientras vale
 * cero, dibuja él el remate redondeado bajo la barra ([ModuleHeaderCap]); con una franja en pantalla
 * es la franja la que redondea y el marco no pinta nada, así que no hay doble redondeo ni corte.
 */
class HeaderBandState {
    internal var count by mutableIntStateOf(0)
    val owned: Boolean get() = count > 0
}

val LocalHeaderBandState = staticCompositionLocalOf<HeaderBandState?> { null }

/**
 * Franja teñida con el tinte del módulo y las esquinas de abajo redondeadas. Va pegada bajo la barra
 * (que lleva el mismo tinte) y reúne lo que cuelga de ella: filtros, búsqueda o tira de días. Una
 * pantalla la pone como primer elemento de su contenido (o fija sobre la lista); al estar en
 * composición avisa al marco para que no dibuje su propio remate (dentro de una lista, véase [DeclareHeaderBand]).
 * Sin módulo no tiene fondo.
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
 * Una pantalla cuya franja va dentro de su lista la declara aquí, fuera de ella: el elemento de la lista sale de
 * composición al desplazar y, sin esto, el marco creería que ya no hay franja y añadiría su remate (el contenido
 * bajaría 20 dp). Sin efecto fuera de un marco.
 */
@Composable
fun DeclareHeaderBand() {
    val state = LocalHeaderBandState.current ?: return
    DisposableEffect(state) {
        state.count++
        onDispose { state.count-- }
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
            .background(moduleTint(module)),
    ) {}
}
