package com.dlunaunizar.bobitos.core.designsystem

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.dlunaunizar.bobitos.core.navigation.BobitosDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** true si el sistema tiene las animaciones desactivadas; lo provee [com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme]. */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * true si el sistema tiene las animaciones desactivadas (escala de duración a 0), para respetar la preferencia de
 * «reducir movimiento» y no aplicar transiciones. Se actualiza en vivo si el usuario la cambia con la app abierta.
 */
@Composable
fun rememberReduceMotion(): Boolean = LocalReduceMotion.current

/** Emite si las animaciones están quitadas al empezar y tras cada cambio de [changes], sin repetir valores. */
internal fun reduceMotionUpdates(readScale: () -> Float, changes: Flow<Unit>): Flow<Boolean> =
    changes.onStart { emit(Unit) }.map { readScale() == 0f }.distinctUntilChanged()

/** Con «Quitar animaciones» la animación es instantánea; si no, se respeta [spec]. */
fun <T> motionSpec(reduceMotion: Boolean, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else spec

/** Muelle con un pequeño rebote: el «con más vida» de pulsar y de la pastilla del día. */
val BouncySpring: SpringSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

private const val PRESSED_SCALE = 0.96f

/** Encoge el elemento a 0,96 mientras se mantiene pulsado, con [BouncySpring]. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, reduceMotion: Boolean): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = motionSpec(reduceMotion, BouncySpring),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Cómo se pasa de una pantalla a otra. */
enum class NavMotion { FADE_THROUGH, SLIDE }

/** Entre pestañas del espacio se funde; al entrar en una pantalla (o salir de ella) se desliza. */
fun navMotionFor(fromRoute: String?, toRoute: String?): NavMotion {
    val tabs = BobitosDestination.workspaceTabs.map { it.route }
    return if (fromRoute in tabs && toRoute in tabs) NavMotion.FADE_THROUGH else NavMotion.SLIDE
}

/**
 * Si el FAB va extendido tras un movimiento de la lista: arriba del todo, sí; bajando, se recoge;
 * subiendo, vuelve; quieto, queda como estaba.
 */
fun fabExpandedAfterScroll(
    wasExpanded: Boolean,
    previousIndex: Int,
    previousOffset: Int,
    index: Int,
    offset: Int,
): Boolean = when {
    index == 0 && offset == 0 -> true
    index != previousIndex -> index < previousIndex
    offset != previousOffset -> offset < previousOffset
    else -> wasExpanded
}

/** true mientras el FAB debe ir extendido: se recoge al bajar por [listState] y vuelve al subir. */
@Composable
fun rememberFabExpanded(listState: LazyListState): Boolean {
    var expanded by remember(listState) { mutableStateOf(true) }
    var previousIndex by remember(listState) { mutableIntStateOf(listState.firstVisibleItemIndex) }
    var previousOffset by remember(listState) { mutableIntStateOf(listState.firstVisibleItemScrollOffset) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                expanded = fabExpandedAfterScroll(expanded, previousIndex, previousOffset, index, offset)
                previousIndex = index
                previousOffset = offset
            }
    }
    return expanded
}
