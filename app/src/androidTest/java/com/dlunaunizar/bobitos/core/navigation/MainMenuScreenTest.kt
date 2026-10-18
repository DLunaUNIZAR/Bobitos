package com.dlunaunizar.bobitos.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.core.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainMenuScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val esEncabezado = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
    private val casa = SpaceSummary(id = "s1", name = "Casa Bobitos", memberCount = 3, role = SpaceRole.OWNER)

    @Composable
    private fun Menu(
        spaces: UiState<List<SpaceSummary>>,
        onEnterSpace: (SpaceSummary) -> Unit = {},
        onManageSpaces: () -> Unit = {},
    ) {
        BobitosTheme {
            MainMenuScreen(
                spaces = spaces,
                syncStatus = SyncStatus.ONLINE,
                onEnterSpace = onEnterSpace,
                onManageSpaces = onManageSpaces,
                onOpen = {},
                onProfile = {},
            )
        }
    }

    @Test
    fun tocarUnEspacioEntraEnEl() {
        val entrados = mutableListOf<SpaceSummary>()
        compose.setContent { Menu(UiState.Content(listOf(casa)), onEnterSpace = { entrados += it }) }

        compose.onNodeWithText("Casa Bobitos · 3 miembros").performClick()

        assertEquals(listOf(casa), entrados)
    }

    @Test
    fun gestionarLlevaATusEspacios() {
        var gestionados = 0
        compose.setContent { Menu(UiState.Content(listOf(casa)), onManageSpaces = { gestionados++ }) }

        compose.onNodeWithText("Gestionar").performClick()

        assertEquals(1, gestionados)
    }

    @Test
    fun sinEspaciosOfreceCrearOUnirse() {
        var gestionados = 0
        compose.setContent { Menu(UiState.Content(emptyList()), onManageSpaces = { gestionados++ }) }

        compose.onNodeWithText("Crear o unirse a un espacio").performClick()

        assertEquals(1, gestionados)
    }

    @Test
    fun tieneTusEspaciosLoTuyoYCatalogosComoEncabezados() {
        compose.setContent { Menu(UiState.Content(listOf(casa))) }

        compose.onNodeWithText("Tus espacios").assert(esEncabezado)
        compose.onNodeWithText("Lo tuyo").assert(esEncabezado)
        compose.onNodeWithText("Catálogos").assert(esEncabezado)
    }
}
