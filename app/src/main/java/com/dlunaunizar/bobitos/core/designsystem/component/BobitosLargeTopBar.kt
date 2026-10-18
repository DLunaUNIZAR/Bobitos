package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.LocalAppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.designsystem.theme.moduleTint
import kotlin.math.roundToInt

/**
 * Barra superior de las pantallas: el título grande es la pantalla (`headlineMedium`) y, al desplazar
 * la lista, se encoge con el [scrollBehavior] (de salida hasta encoger) hasta dejar solo la fila de iconos
 * con el título pequeño (`titleLarge`).
 *
 * Se compone de dos partes. La fila de iconos es un `TopAppBar` de Material de 64 dp: a la izquierda la
 * flecha atrás o el [spaceSelector], a la derecha las [actions]. Debajo va la zona abierta: el [eyebrow]
 * (el saludo de Hoy) y el título grande, de hasta dos líneas, y bajo ellos la [band] de la pantalla
 * (filtros, búsqueda), que se encoge con la zona. La zona se mide y fija el recorrido del
 * [scrollBehavior] con su alto; al encoger se recorta por arriba y el título sube con ella.
 *
 * Al encoger, el [spaceSelector] se funde y el título pequeño aparece en su sitio. Pasada la mitad el
 * selector sale de la composición (ni toques ni TalkBack), y el título se lee una sola vez: es encabezado
 * el grande hasta que está casi recortado y después el pequeño. Nada lee el encogimiento al componer,
 * salvo esos dos cambios de umbral: las alfas y el recorte se leen al dibujar y al medir.
 *
 * El contenedor, abierto y encogido, es el tinte del módulo de [LocalAppModule]; sin módulo es la superficie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BobitosLargeTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    spaceSelector: (@Composable () -> Unit)? = null,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    band: (@Composable ColumnScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val module = LocalAppModule.current
    val container = module?.let { moduleTint(it) } ?: MaterialTheme.colorScheme.surface
    val state = scrollBehavior.state
    val collapsed by remember(state) { derivedStateOf { state.collapsedFraction >= HALF } }
    val smallTitle by remember(state) { derivedStateOf { state.collapsedFraction >= TITLE_SWAP } }
    Column(modifier = modifier) {
        TopAppBar(
            title = {
                RowTitle(title, spaceSelector, state, collapsed, smallTitle)
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                }
            },
            actions = actions,
            expandedHeight = TopAppBarDefaults.TopAppBarExpandedHeight,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = container, scrolledContainerColor = container),
        )
        CollapsingZone(state, container) {
            Column {
                Column(
                    modifier = Modifier
                        .padding(horizontal = Spacing.lg)
                        .padding(bottom = if (band == null) Spacing.lg else Spacing.sm),
                ) {
                    if (eyebrow != null) {
                        Text(
                            text = eyebrow,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .graphicsLayer { alpha = 1f - state.collapsedFraction }
                            .titleSemantics(isHeading = !smallTitle),
                    )
                }
                band?.invoke(this)
            }
        }
    }
}

// A partir de esta fracción de encogimiento el selector del espacio desaparece (y el título pequeño ya se ve).
private const val HALF = 0.5f

// A partir de esta otra el título grande, ya casi recortado, deja de ser el encabezado y lo es el pequeño.
private const val TITLE_SWAP = 0.75f

/** El título pequeño y, abierta la barra, el selector del espacio en el mismo sitio: uno se funde y el otro aparece. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowTitle(
    title: String,
    spaceSelector: (@Composable () -> Unit)?,
    state: TopAppBarState,
    collapsed: Boolean,
    smallTitle: Boolean,
) {
    Box(contentAlignment = Alignment.CenterStart) {
        if (spaceSelector != null && !collapsed) {
            Box(modifier = Modifier.graphicsLayer { alpha = (1f - 2f * state.collapsedFraction).coerceIn(0f, 1f) }) {
                spaceSelector()
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .graphicsLayer { alpha = (2f * state.collapsedFraction - 1f).coerceIn(0f, 1f) }
                .titleSemantics(isHeading = smallTitle),
        )
    }
}

// El título que no se ve no existe para TalkBack: el otro es el encabezado.
private fun Modifier.titleSemantics(isHeading: Boolean): Modifier =
    if (isHeading) semantics { heading() } else clearAndSetSemantics {}

/**
 * Zona abierta bajo la fila de iconos. Mide su contenido entero, fija con su alto el recorrido del
 * encogimiento y se queda con la parte que deja el [TopAppBarState.heightOffset]: el contenido sube y se
 * recorta por arriba, sin cambiar de estilo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsingZone(state: TopAppBarState, container: Color, content: @Composable () -> Unit) {
    Layout(
        content = content,
        modifier = Modifier.background(container).clipToBounds(),
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val limit = -placeable.height.toFloat()
        if (state.heightOffsetLimit != limit) state.heightOffsetLimit = limit
        val offset = state.heightOffset.coerceIn(limit, 0f).roundToInt()
        layout(placeable.width, placeable.height + offset) { placeable.place(0, offset) }
    }
}
