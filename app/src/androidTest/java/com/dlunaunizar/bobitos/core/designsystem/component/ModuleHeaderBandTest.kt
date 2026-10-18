package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class ModuleHeaderBandTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var listState: LazyListState

    private fun contentTop() = compose.onNodeWithTag("contenido").fetchSemanticsNode().boundsInWindow.top

    private fun capBounds() = compose.onNodeWithTag("moduleHeaderCap").fetchSemanticsNode().boundsInWindow

    private fun swipeList(endY: Float = 0.2f) {
        compose.onNodeWithTag("lista").performTouchInput { swipeUp(startY = height * 0.8f, endY = height * endY) }
    }

    // Como las pantallas con la búsqueda en la franja de la cabecera y una lista larga.
    private fun mostrarListaConFranja(
        lista: LazyListScope.() -> Unit = { items((1..60).toList()) { Text("Fila $it") } },
    ) {
        compose.setContent {
            BobitosTheme {
                ScreenScaffold(title = "Prueba", onBack = null, module = AppModule.TASKS) { padding ->
                    listState = rememberLazyListState()
                    Box(Modifier.fillMaxSize().padding(padding).testTag("contenido")) {
                        CollapsibleHeaderBand {
                            SearchField(
                                query = "",
                                onQueryChange = {},
                                visible = true,
                                modifier = Modifier.testTag("busqueda"),
                            )
                        }
                        LazyColumn(state = listState, modifier = Modifier.testTag("lista"), content = lista)
                    }
                }
            }
        }
    }

    @Test
    fun alDesplazarLaFranjaSeVaConLaCabeceraYQuedaElRemate() {
        mostrarListaConFranja()
        compose.onNodeWithTag("busqueda").assertExists()
        val remateAbierto = capBounds().top

        swipeList()
        swipeList()
        compose.waitForIdle()

        compose.onNodeWithTag("busqueda").assertIsNotDisplayed()
        compose.onNodeWithTag("moduleHeaderCap").assertHeightIsEqualTo(20.dp)
        assertTrue("el remate sube con la barra", capBounds().top < remateAbierto)
        assertTrue("el remate queda bajo la barra encogida", capBounds().top > 0f)
    }

    @Test
    fun soltarAMedioCaminoNoDejaLaBusquedaPartida() {
        mostrarListaConFranja()
        val alto = compose.onNodeWithTag("busqueda").fetchSemanticsNode().boundsInWindow.height
        val ochenta = 80 * compose.activity.resources.displayMetrics.density

        compose.onNodeWithTag("lista").performTouchInput { swipeUp(startY = centerY, endY = centerY - ochenta) }
        compose.waitForIdle()

        val visible = compose.onAllNodesWithTag("busqueda").fetchSemanticsNodes().firstOrNull()
            ?.boundsInWindow?.height ?: 0f
        assertTrue("la búsqueda queda partida ($visible de $alto px)", visible < 1f || abs(visible - alto) < 1.5f)
    }

    @Test
    fun ningunaFilaAsomaEntreLaCabeceraYLaCabeceraFija() {
        mostrarListaConFranja {
            (1..5).forEach { grupo ->
                stickyHeader(key = "grupo-$grupo") {
                    SectionHeader(
                        "Grupo $grupo",
                        sticky = true,
                        horizontalPadding = Spacing.lg,
                        modifier = Modifier.testTag("sticky"),
                    )
                }
                items((1..6).toList(), key = { "fila-$grupo-$it" }) {
                    Text(
                        "Fila $grupo.$it",
                        Modifier.height(56.dp).padding(horizontal = Spacing.lg).testTag("row-$grupo-$it"),
                    )
                }
            }
        }
        swipeList()
        swipeList(0.5f)
        compose.waitForIdle()

        val lista = compose.onNodeWithTag("lista").fetchSemanticsNode().boundsInWindow
        val sticky = compose.onAllNodesWithTag("sticky").fetchSemanticsNodes().map { it.boundsInWindow }
            .minByOrNull { it.top }!!
        val franjaTop = capBounds().bottom
        assertEquals("la cabecera fija ocupa todo el ancho", lista.width, sticky.width, 1f)
        (1..5).flatMap { g -> (1..6).map { "row-$g-$it" } }.forEach { tag ->
            val fila =
                compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.boundsInWindow ?: return@forEach
            if (fila.height <= 0f) return@forEach
            assertFalse(
                "la fila $tag asoma entre la barra ($franjaTop) y la cabecera fija (${sticky.top})",
                fila.top < sticky.top && fila.bottom > franjaTop,
            )
        }
    }

    @Test
    fun alSalirLaFranjaDeLaVistaElContenidoNoSeMueve() {
        mostrarListaConFranja()
        compose.waitForIdle()
        val antes = contentTop()

        // Desplazar por código no encoge la barra: cualquier cambio de `top` sería el remate del marco.
        compose.runOnIdle { runBlocking { listState.scrollToItem(30) } }
        compose.waitForIdle()

        assertTrue("la lista se desplazó", listState.firstVisibleItemIndex > 0)
        assertEquals(antes, contentTop(), 0.5f)
    }
}
