package com.dlunaunizar.bobitos.core.designsystem.theme

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.dlunaunizar.bobitos.core.designsystem.component.ScreenScaffold
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModuleThemeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class Seen(var primary: Color = Color.Unspecified, var container: Color = Color.Unspecified)

    private fun seenIn(module: AppModule?, dark: Boolean = false): Seen {
        val seen = Seen()
        compose.setContent {
            BobitosTheme(darkTheme = dark) {
                ScreenScaffold(title = "Pantalla", onBack = null, module = module) {
                    seen.primary = MaterialTheme.colorScheme.primary
                    seen.container = MaterialTheme.colorScheme.primaryContainer
                }
            }
        }
        compose.waitForIdle()
        return seen
    }

    @Test fun unaPantallaDeModuloUsaSuColor() {
        listOf(AppModule.MEALS, AppModule.TASKS).forEach { module ->
            val expected = ModulePalette.colors(module, dark = false)
            compose.activityRule.scenario.recreate()
            val seen = seenIn(module)
            assertEquals(expected.accent, seen.primary)
            assertEquals(expected.container, seen.container)
        }
    }

    @Test fun unaPantallaGlobalConservaLaMarca() {
        var brand = Color.Unspecified
        var seen = Seen()
        compose.setContent {
            BobitosTheme(darkTheme = false) {
                brand = MaterialTheme.colorScheme.primary
                ScreenScaffold(title = "Global", onBack = null, module = null) {
                    seen = Seen(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                }
            }
        }
        compose.waitForIdle()
        assertEquals(brand, seen.primary)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun unaHojaAbiertaDesdeElModuloHeredaSuColor() {
        var inSheet = Color.Unspecified
        compose.setContent {
            BobitosTheme(darkTheme = false) {
                ScreenScaffold(title = "Pantalla", onBack = null, module = AppModule.SPORT) {
                    ModalBottomSheet(onDismissRequest = {}) {
                        inSheet = MaterialTheme.colorScheme.primary
                        Text("Hoja")
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(ModulePalette.colors(AppModule.SPORT, dark = false).accent, inSheet)
    }
}
