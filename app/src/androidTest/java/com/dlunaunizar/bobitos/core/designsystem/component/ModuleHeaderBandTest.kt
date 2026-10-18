package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ModuleHeaderBandTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var listState: LazyListState

    private fun contentTop() = compose.onNodeWithTag("contenido").fetchSemanticsNode().boundsInWindow.top

    // Como las seis pantallas con la franja de filtros como primer elemento de una lista larga.
    private fun mostrarListaConFranja() {
        compose.setContent {
            BobitosTheme {
                ScreenScaffold(title = "Prueba", onBack = null, module = AppModule.TASKS) { padding ->
                    listState = rememberLazyListState()
                    Box(Modifier.fillMaxSize().padding(padding).testTag("contenido")) {
                        DeclareHeaderBand()
                        LazyColumn(state = listState) {
                            item(key = "filters") { ModuleHeaderBand { Text("Filtros") } }
                            items((1..60).toList()) { Text("Fila $it") }
                        }
                    }
                }
            }
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

        assertTrue("la franja sigue a la vista", listState.firstVisibleItemIndex > 0)
        assertEquals(antes, contentTop(), 0.5f)
    }
}
