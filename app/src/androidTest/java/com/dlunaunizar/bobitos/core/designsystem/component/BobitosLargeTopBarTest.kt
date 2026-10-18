package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class BobitosLargeTopBarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var state: TopAppBarState
    private var headlineSize = 0f
    private var titleLargeSize = 0f
    private val selectorLabel = "Cambiar de espacio, Casa Bobitos"
    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    /** Barras apiladas, una por título; `state` es el de la primera. Cada una lleva la etiqueta `barra-<título>`. */
    private fun show(
        vararg titles: String,
        widthDp: Int = 411,
        fontScale: Float = 1f,
        eyebrow: String? = null,
        withSelector: Boolean = false,
        twoActions: Boolean = false,
    ) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                BobitosTheme {
                    headlineSize = MaterialTheme.typography.headlineMedium.fontSize.value
                    titleLargeSize = MaterialTheme.typography.titleLarge.fontSize.value
                    Column(Modifier.width(widthDp.dp)) {
                        titles.forEachIndexed { i, title ->
                            val barState = rememberTopAppBarState()
                            if (i == 0) state = barState
                            val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(barState)
                            BobitosLargeTopBar(
                                title = title,
                                scrollBehavior = behavior,
                                modifier = Modifier.testTag("barra-$title"),
                                eyebrow = eyebrow,
                                spaceSelector = if (withSelector) {
                                    { Selector() }
                                } else {
                                    null
                                },
                                actions = {
                                    if (twoActions) {
                                        IconButton(onClick = {}) { Icon(Icons.Rounded.Bolt, "Rayo") }
                                        IconButton(onClick = {}) { Icon(Icons.Rounded.Settings, "Ajustes") }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Selector() {
        Row(
            Modifier
                .clickable(onClick = {})
                .semantics { contentDescription = selectorLabel },
        ) { Text("Casa Bobitos", style = MaterialTheme.typography.labelLarge) }
    }

    private fun collapseTo(fraction: Float) {
        compose.runOnIdle { state.heightOffset = state.heightOffsetLimit * fraction }
        compose.waitForIdle()
    }

    private fun bounds(text: String): Rect = compose.onNode(hasText(text)).fetchSemanticsNode().boundsInWindow

    private fun barHeight(title: String): Float =
        compose.onNodeWithTag("barra-$title").fetchSemanticsNode().size.height.toFloat()

    private fun SemanticsNode.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.first()
    }

    @Test
    fun sinFranjaLaZonaTeñidaOcupaTodoElAncho() {
        show("Notas")
        val barra = compose.onNodeWithTag("barra-Notas").fetchSemanticsNode().size.width
        val zona = compose.onNodeWithTag("collapsingZone").fetchSemanticsNode().size.width
        assertEquals("ancho de la zona abierta", barra.toFloat(), zona.toFloat(), compose.density.density)
    }

    @Test
    fun elSelectorVaEnLaFilaDeLosIconos() {
        show("Tareas", withSelector = true, twoActions = true)
        val selector = compose.onNodeWithContentDescription(selectorLabel).fetchSemanticsNode().boundsInWindow
        val action = compose.onNodeWithContentDescription("Rayo").fetchSemanticsNode().boundsInWindow
        val density = compose.density.density
        assertTrue("centros distintos", kotlin.math.abs(selector.center.y - action.center.y) < 4 * density)
        val title = compose.onNode(hasText("Tareas") and heading).fetchSemanticsNode().boundsInWindow
        assertTrue("el selector no queda sobre el título", selector.bottom <= title.top)
    }

    @Test
    fun elSaludoVaEncimaDelTitulo() {
        show("Sábado, 17 de octubre", eyebrow = "Buenas tardes, David")
        assertTrue(bounds("Buenas tardes, David").bottom <= bounds("Sábado, 17 de octubre").top)
    }

    @Test
    fun unTituloQueCabeEnUnaLineaNoReservaDos() {
        show("Ajustes del espacio", "Notas")
        assertEquals(barHeight("Notas"), barHeight("Ajustes del espacio"), compose.density.density)
    }

    @Test
    fun unTituloQueNoCabeVaEnDosLineasSinCortarse() {
        // Con Nunito, «Pimientos del piquillo» aún cabe en una línea a 360 dp y 130 %: se usa uno más largo.
        show("Pimientos del piquillo rellenos de bacalao", "Notas", widthDp = 360, fontScale = 1.3f)
        val layout = compose.onNode(
            hasText("Pimientos del piquillo rellenos de bacalao") and heading,
        ).fetchSemanticsNode().layout()
        assertEquals(2, layout.lineCount)
        assertFalse("el título se corta", layout.isLineEllipsized(1) || layout.didOverflowHeight)
        assertTrue("la barra no crece", barHeight("Pimientos del piquillo rellenos de bacalao") > barHeight("Notas"))
    }

    @Test
    fun aMedioEncogerElTituloSigueGrandeYSube() {
        show("Notas")
        val abierto = compose.onNode(hasText("Notas")).fetchSemanticsNode().positionInWindow.y
        collapseTo(0.6f)
        val grande = compose.onNode(hasText("Notas") and heading).fetchSemanticsNode()
        assertEquals(headlineSize, grande.layout().layoutInput.style.fontSize.value, 0.01f)
        assertTrue(
            "el título no sube (los límites visibles se recortan, por eso la posición)",
            grande.positionInWindow.y < abierto,
        )
    }

    @Test
    fun encogidaQuedaElTituloPequenoYElSelectorNoSeToca() {
        show("Tareas", withSelector = true)
        collapseTo(1f)
        val titulo = compose.onNode(hasText("Tareas") and heading).fetchSemanticsNode()
        assertEquals(titleLargeSize, titulo.layout().layoutInput.style.fontSize.value, 0.01f)
        compose.onAllNodes(hasClickAction() and hasContentDescription(selectorLabel)).assertCountEquals(0)
    }

    @Test
    fun elTituloSeLeeUnaSolaVez() {
        show("Notas")
        for (f in listOf(0f, 1f)) {
            collapseTo(f)
            compose.onAllNodes(hasText("Notas") and heading).assertCountEquals(1)
        }
    }

    /** Barra con su `scrollBehavior` sobre una lista larga o, sin ella, sobre un contenido que no se desplaza. */
    private fun showWithContent(scrollable: Boolean, onAction: () -> Unit = {}) {
        compose.setContent {
            BobitosTheme {
                val barState = rememberTopAppBarState()
                state = barState
                val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(barState)
                Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
                    BobitosLargeTopBar(
                        title = "Tareas",
                        scrollBehavior = behavior,
                        modifier = Modifier.testTag("barra"),
                        actions = { IconButton(onClick = onAction) { Icon(Icons.Rounded.Bolt, "Rayo") } },
                    )
                    if (scrollable) {
                        LazyColumn { items((1..100).toList()) { Text("Fila $it") } }
                    } else {
                        Box(Modifier.fillMaxSize()) { Text("Vacío") }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun arrastrarLaCabeceraHaciaArribaLaEncoge() {
        showWithContent(scrollable = true)
        compose.onNodeWithTag("barra").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertEquals(1f, state.collapsedFraction, 0.01f)
    }

    @Test
    fun encogidaSobreUnVacioArrastrarHaciaAbajoLaAbre() {
        showWithContent(scrollable = false)
        compose.runOnIdle { state.heightOffset = state.heightOffsetLimit }
        compose.waitForIdle()
        assertEquals(1f, state.collapsedFraction, 0.01f)
        compose.onNodeWithTag("barra").performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertEquals(0f, state.collapsedFraction, 0.01f)
    }

    @Test
    fun unToqueEnUnaAccionDeLaBarraSigueLlegando() {
        var toques = 0
        showWithContent(scrollable = true, onAction = { toques++ })
        compose.onNodeWithContentDescription("Rayo").performClick()
        assertEquals(1, toques)
    }
}
