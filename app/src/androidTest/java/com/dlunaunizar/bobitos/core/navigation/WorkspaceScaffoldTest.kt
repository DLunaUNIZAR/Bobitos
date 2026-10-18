package com.dlunaunizar.bobitos.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.core.designsystem.component.TopBarActions
import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.SyncStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkspaceScaffoldTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var pickerOpened = false

    private fun switcher() = SpaceSwitcher(
        spaces = emptyList(),
        selectedSpaceId = null,
        pickerOpen = false,
        onPickerOpenChange = { if (it) pickerOpened = true },
        onSelect = {},
    )

    @Composable
    private fun Scaffold(
        title: String,
        spaceName: String,
        module: AppModule? = AppModule.TASKS,
        content: @Composable () -> Unit = {},
    ) {
        WorkspaceScaffold(
            selectedTab = BobitosDestination.Tasks,
            title = title,
            module = module,
            spaceName = spaceName,
            onTabSelected = {},
            switcher = switcher(),
            onManageSpaces = {},
            onSpaceSettings = {},
            onProfile = {},
            syncStatus = SyncStatus.ONLINE,
            content = content,
        )
    }

    @Test
    fun elTituloEsLaPantallaYEsEncabezado() {
        compose.setContent { BobitosTheme { Scaffold("Tareas", "Casa Bobitos") } }
        compose.onNode(hasText("Tareas") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertIsDisplayed()
    }

    @Test
    fun elEspacioEsUnSelector() {
        pickerOpened = false
        compose.setContent { BobitosTheme { Scaffold("Tareas", "Casa Bobitos") } }
        val selector = compose.onNodeWithContentDescription("Cambiar de espacio, Casa Bobitos")
        selector.assertHasClickAction()
        assertTrue(selector.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role) == Role.Button)
        selector.performClick()
        assertTrue(pickerOpened)
    }

    @Test
    fun lasAccionesDeLaPantallaVanEnLaBarra() {
        compose.setContent {
            BobitosTheme {
                Scaffold("Tareas", "Casa Bobitos") {
                    TopBarActions {
                        IconButton(onClick = {}) {
                            Icon(Icons.Rounded.Bolt, contentDescription = "Plantillas de tareas")
                        }
                    }
                }
            }
        }
        compose.onNode(hasContentDescription("Plantillas de tareas")).assertIsDisplayed()
    }

    @Test
    fun conLetraGrandeYUnEspacioLargoNadaSeCorta() {
        val espacio = "Piso de estudiantes de Zaragoza"
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                BobitosTheme {
                    Box(Modifier.width(360.dp)) {
                        Scaffold("Tareas", espacio) {
                            TopBarActions {
                                IconButton(onClick = {}) {
                                    Icon(Icons.Rounded.Bolt, contentDescription = "Plantillas de tareas")
                                }
                            }
                            Text("cuerpo")
                        }
                    }
                }
            }
        }
        val titleNode = compose.onNode(
            hasText("Tareas") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
        ).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        titleNode.config.getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action?.invoke(results)
        // hasVisualOverflow da falso positivo con ancho de línea menor que el máximo; se comprueba lo que importa.
        val layout = results.first()
        assertFalse("el título se corta", layout.isLineEllipsized(0) || layout.didOverflowHeight)
        assertTrue("el título se parte", layout.lineCount == 1)
        val selector = compose.onNodeWithContentDescription("Cambiar de espacio, $espacio")
            .fetchSemanticsNode().boundsInWindow
        val body = compose.onNodeWithText("cuerpo").fetchSemanticsNode().boundsInWindow
        assertTrue("el título invade el contenido", titleNode.boundsInWindow.bottom <= body.top)
        assertFalse("el selector se solapa con el título", selector.overlaps(titleNode.boundsInWindow))
        listOf("Plantillas de tareas", "Gestionar", "Perfil").forEach { action ->
            val bounds = compose.onNode(hasContentDescription(action, substring = true)).fetchSemanticsNode()
                .boundsInWindow
            assertFalse("el selector se solapa con «$action»", selector.overlaps(bounds))
        }
    }
}
