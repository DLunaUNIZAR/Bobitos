package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FabClearanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun setModule(showSnackbar: Boolean = false) {
        compose.setContent {
            BobitosTheme {
                Box(Modifier.size(411.dp, 700.dp)) {
                    ModuleScaffold(
                        floatingActionButton = {
                            ExtendedFloatingActionButton(onClick = {}) { Text("Nuevo") }
                        },
                    ) { _ ->
                        if (showSnackbar) {
                            val host = LocalSnackbarHostState.current
                            LaunchedEffect(Unit) { host?.showSnackbar("Aviso de prueba") }
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = Spacing.fabClearance),
                        ) {
                            items((0 until 40).toList()) { i -> Text("Fila $i") }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun theLastRowEndsAboveTheFab() {
        setModule()
        compose.onNode(hasScrollAction()).performScrollToIndex(39)
        val last = compose.onNodeWithText("Fila 39").fetchSemanticsNode().boundsInWindow.bottom
        val fabTop = compose.onNodeWithText("Nuevo").fetchSemanticsNode().boundsInWindow.top
        assertTrue("fila $last > fab $fabTop", last <= fabTop)
    }

    // M3 coloca el aviso encima del FAB (el FAB no se tapa ni se solapa) y ambos quedan dentro del módulo.
    @Test
    fun theFabRisesAboveASnackbar() {
        setModule(showSnackbar = true)
        compose.waitForIdle()
        val fab = compose.onNodeWithText("Nuevo").fetchSemanticsNode().boundsInWindow
        val snack = compose.onNodeWithText("Aviso de prueba").fetchSemanticsNode().boundsInWindow
        assertTrue("fab $fab se solapa con el aviso $snack", snack.bottom <= fab.top || fab.bottom <= snack.top)
    }
}
