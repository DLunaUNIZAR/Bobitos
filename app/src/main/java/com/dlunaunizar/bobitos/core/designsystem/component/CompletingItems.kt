package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.BouncySpring
import com.dlunaunizar.bobitos.core.designsystem.motionSpec
import com.dlunaunizar.bobitos.core.designsystem.rememberReduceMotion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val HOLD_MS = 450L

// Lo máximo que se espera a que se confirme la escritura antes de sacar la fila.
private const val WRITE_CAP_MS = 8_000L
private const val EXIT_MS = 260
private const val CHECK_FROM_SCALE = 0.7f
private const val EXIT_SHIFT_FRACTION = 0.25f
private const val EXIT_TILT_DEGREES = 4f
private const val CASCADE_STEP_MS = 50L
private const val CASCADE_MAX_STEPS = 8
private val CascadeShift = 14.dp

/**
 * Filas que se acaban de completar. La escritura la lanza quien llama, al momento y sin esperar a la animación (varias
 * seguidas escriben todas), y avisa con [start] al lanzarla y con [finish] cuando acaba. La fila se mantiene visible y
 * marcada con estado local, aunque Firestore ya la haya quitado de la lista, y sale deslizándose cuando se cumplen las
 * dos cosas: pasaron unos 450 ms y la escritura se confirmó (las transacciones no cambian la caché hasta entonces).
 * Si la escritura falla, la fila vuelve a su estado sin salir; si no acaba en unos 8 s, sale igualmente.
 */
@Stable
class CompletingItems internal constructor(private val scope: CoroutineScope, private val haptic: HapticFeedback) {
    // id -> true cuando ya está saliendo.
    private val phases = mutableStateMapOf<String, Boolean>()

    // Cómo acabó la escritura de cada fila en curso (true si bien); se completa en [finish].
    private val results = mutableMapOf<String, CompletableDeferred<Boolean>>()

    // Lo último que se vio de cada fila y su orden, para devolverla a su sitio cuando la lista ya no la trae.
    private val lastSeen = mutableMapOf<String, Any?>()
    private var lastOrder: List<String> = emptyList()

    /** Marca [id] como completándose, con una vibración corta. */
    fun start(id: String) {
        if (id in phases) return
        val result = CompletableDeferred<Boolean>()
        results[id] = result
        phases[id] = false
        haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
        scope.launch {
            delay(HOLD_MS)
            val written = withTimeoutOrNull(WRITE_CAP_MS) { result.await() } ?: true
            // Si falló, [finish] ya la devolvió a su estado; si empezó otra vuelta con el mismo id, no es esta.
            if (!written || results[id] !== result) return@launch
            phases[id] = true
            delay(EXIT_MS.toLong())
            phases.remove(id)
            results.remove(id)
        }
    }

    /** La escritura de [id] acabó; si falló ([success] false), la fila deja de estar marcada y no sale. */
    fun finish(id: String, success: Boolean) {
        val result = results[id] ?: return
        if (!success) {
            phases.remove(id)
            results.remove(id)
        }
        result.complete(success)
    }

    /**
     * Completa [id] con vida: la marca antes de que la escritura pueda acabar y lanza la escritura con [launch], que
     * recibe el aviso de que acabó y devuelve si llegó a lanzarse. Si se lanzó, [celebration] sabe que lo completó el
     * usuario; si no, la fila vuelve a su estado. Volver a tocar una que se está completando no hace nada.
     */
    fun begin(id: String, celebration: CelebrationState, launch: (onFinished: (Boolean) -> Unit) -> Boolean) {
        if (isCompleting(id)) return
        start(id)
        celebration.userCompleted(id)
        val launched = launch { ok ->
            if (!ok) celebration.userCancelled(id)
            finish(id, ok)
        }
        if (!launched) {
            celebration.userCancelled(id)
            finish(id, false)
        }
    }

    fun isCompleting(id: String): Boolean = id in phases

    internal fun isLeaving(id: String): Boolean = phases[id] == true

    /**
     * [current] con las filas que se están completando en su sitio y como estaban (pendientes), aunque la lista ya
     * las haya quitado o movido a «Hecho».
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> retain(current: List<T>, idOf: (T) -> String): List<T> {
        val currentIds = current.map(idOf).toSet()
        current.forEach { if (idOf(it) !in phases) lastSeen[idOf(it)] = it }
        lastSeen.keys.retainAll(currentIds + phases.keys)
        val result = current.map { item -> (if (idOf(item) in phases) lastSeen[idOf(item)] as T? else null) ?: item }
            .toMutableList()
        phases.keys.filter { it !in currentIds }.forEach { id ->
            val old = lastSeen[id] as T? ?: return@forEach
            result.add(lastOrder.indexOf(id).coerceIn(0, result.size), old)
        }
        lastOrder = result.map(idOf)
        return result
    }
}

@Composable
fun rememberCompletingItems(): CompletingItems {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    return remember { CompletingItems(scope, haptic) }
}

/** Sale de la fila completada: se desplaza a la derecha con un giro de 4° y se desvanece. */
@Composable
fun Modifier.completingExit(items: CompletingItems, id: String): Modifier {
    val reduceMotion = rememberReduceMotion()
    val progress by animateFloatAsState(
        targetValue = if (items.isLeaving(id)) 1f else 0f,
        animationSpec = motionSpec(reduceMotion, tween(EXIT_MS, easing = FastOutLinearInEasing)),
        label = "completingExit",
    )
    return graphicsLayer {
        translationX = size.width * EXIT_SHIFT_FRACTION * progress
        rotationZ = EXIT_TILT_DEGREES * progress
        alpha = 1f - progress
    }
}

/** La casilla rebota con [BouncySpring] cuando [active] pasa a true. */
@Composable
fun Modifier.checkBounce(active: Boolean): Modifier {
    val reduceMotion = rememberReduceMotion()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active && !reduceMotion) {
            scale.snapTo(CHECK_FROM_SCALE)
            scale.animateTo(1f, BouncySpring)
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Entrada en cascada: la fila [index] espera 50 ms por posición y entra con fundido y 14 dp de desplazamiento con
 * [BouncySpring]. Se repite cuando cambia [key] (otro día); con [play] false o sin animaciones, aparece ya puesta.
 */
@Composable
fun Modifier.cascadeIn(index: Int, key: Any? = Unit, play: Boolean = true): Modifier {
    val reduceMotion = rememberReduceMotion()
    val progress = remember(key) { Animatable(if (play && !reduceMotion) 0f else 1f) }
    LaunchedEffect(key) {
        if (progress.value < 1f) {
            delay(index.coerceIn(0, CASCADE_MAX_STEPS) * CASCADE_STEP_MS)
            progress.animateTo(1f, BouncySpring)
        }
    }
    val shift = with(LocalDensity.current) { CascadeShift.toPx() }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * shift
    }
}
