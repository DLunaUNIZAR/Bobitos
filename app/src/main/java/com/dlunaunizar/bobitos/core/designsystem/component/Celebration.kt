package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.rememberReduceMotion
import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.LocalDarkTheme
import com.dlunaunizar.bobitos.core.designsystem.theme.ModulePalette
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private const val PARTICLES = 18
private const val TOTAL_MS = 1_500
private const val TEXT_ONLY_MS = 1_800L

// Las partículas acaban dentro de los primeros 900 ms (el 60 % del total); el texto se queda más.
private const val PARTICLE_SHARE = 0.6f
private const val TEXT_FADE_IN = 0.12f
private const val TEXT_FADE_OUT = 0.25f
private const val TEXT_FROM_SCALE = 0.92f
private const val TEXT_SCALE_SHARE = 0.2f
private val MinReach = 90.dp
private val ExtraReach = 90.dp
private val MinSize = 3.dp
private val ExtraSize = 3.dp

/**
 * Hay que celebrar cuando alguna de las filas que [completedByUser] (las que acaba de completar el propio usuario;
 * vacío si el cambio no fue suyo: una carga inicial o un miembro remoto) estaba entre las pendientes y ya no queda
 * ninguna.
 */
fun shouldCelebrate(completedByUser: Set<String>, pendingBefore: Set<String>, pendingAfter: Set<String>): Boolean =
    pendingAfter.isEmpty() && pendingBefore.any { it in completedByUser }

/**
 * Recuerda qué filas tocó el usuario y si toca mostrar la celebración. Un toque se olvida cuando la fila sale de las
 * pendientes (o la escritura falla), no con cualquier lista nueva: varias seguidas, o una emisión ajena entre medias,
 * no lo borran.
 */
@Stable
class CelebrationState {
    private val tapped = mutableSetOf<String>()
    private var previous: Set<String>? = null
    var visible by mutableStateOf(false)
        private set

    /** El usuario acaba de completar [id]. */
    fun userCompleted(id: String) {
        tapped += id
    }

    /** La escritura de [id] falló o no llegó a lanzarse: ya no cuenta como completada por el usuario. */
    fun userCancelled(id: String) {
        tapped -= id
    }

    internal fun onPending(pending: Set<String>) {
        val before = previous
        previous = pending
        if (before != null && shouldCelebrate(tapped, before, pending)) visible = true
        // Los toques de las filas que ya salieron (o que ya no están) se olvidan.
        tapped.retainAll(pending)
    }

    fun dismiss() {
        visible = false
    }
}

/**
 * [pending] son los ids que siguen pendientes; [source] cambia con cada lista nueva (aunque el conjunto sea el mismo)
 * para limpiar el toque. La primera lista que se ve no celebra.
 */
@Composable
fun rememberCelebrationState(source: Any?, pending: Set<String>): CelebrationState {
    val state = remember { CelebrationState() }
    LaunchedEffect(source) { state.onPending(pending) }
    return state
}

/**
 * Celebración breve: un texto centrado (región viva cortés, para que TalkBack lo diga) y unas 18 partículas con los
 * acentos de los siete módulos que salen del centro y se desvanecen en menos de 1 s. No intercepta toques. Con
 * «Quitar animaciones» queda solo el texto, que se va solo. Llama a [onFinished] al terminar.
 */
@Composable
fun Celebration(visible: Boolean, message: String, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    if (visible) CelebrationContent(message, onFinished, modifier)
}

@Composable
private fun CelebrationContent(message: String, onFinished: () -> Unit, modifier: Modifier) {
    val reduceMotion = rememberReduceMotion()
    val progress = remember { Animatable(0f) }
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            delay(TEXT_ONLY_MS)
        } else {
            progress.animateTo(1f, tween(TOTAL_MS, easing = LinearEasing))
        }
        finished()
    }
    val dark = LocalDarkTheme.current
    val particles = remember(dark) { celebrationParticles(dark) }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!reduceMotion) {
            val minReach = with(LocalDensity.current) { MinReach.toPx() }
            Canvas(Modifier.fillMaxSize()) {
                val burst = (progress.value / PARTICLE_SHARE).coerceIn(0f, 1f)
                val eased = 1f - (1f - burst).pow(3)
                particles.forEach { particle ->
                    val reach = minReach + particle.reach * ExtraReach.toPx()
                    val distance = reach * eased
                    drawCircle(
                        color = particle.color,
                        radius = MinSize.toPx() + particle.size * ExtraSize.toPx(),
                        center = center + Offset(cos(particle.angle) * distance, sin(particle.angle) * distance),
                        alpha = (1f - burst).pow(2),
                    )
                }
            }
        }
        val fade = if (reduceMotion) {
            1f
        } else {
            min(progress.value / TEXT_FADE_IN, (1f - progress.value) / TEXT_FADE_OUT).coerceIn(0f, 1f)
        }
        val scale = if (reduceMotion) {
            1f
        } else {
            TEXT_FROM_SCALE + (1f - TEXT_FROM_SCALE) * min(progress.value / TEXT_SCALE_SHARE, 1f)
        }
        // Un Box y no un Surface: el Surface consume los toques y taparía lo de debajo durante la celebración.
        val shape = MaterialTheme.shapes.extraLarge
        Box(
            Modifier
                .graphicsLayer {
                    alpha = fade
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(6.dp, shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = Spacing.xl, vertical = Spacing.md)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

private class Particle(val angle: Float, val reach: Float, val size: Float, val color: Color)

// Repartidas en círculo con un pequeño desorden fijo; los colores recorren los siete módulos.
private fun celebrationParticles(dark: Boolean): List<Particle> {
    val random = Random(PARTICLES)
    val modules = AppModule.entries
    return List(PARTICLES) { i ->
        Particle(
            angle = (i + random.nextFloat() * 0.6f) / PARTICLES * (2f * Math.PI.toFloat()),
            reach = random.nextFloat(),
            size = random.nextFloat(),
            color = ModulePalette.colors(modules[i % modules.size], dark).accent,
        )
    }
}
