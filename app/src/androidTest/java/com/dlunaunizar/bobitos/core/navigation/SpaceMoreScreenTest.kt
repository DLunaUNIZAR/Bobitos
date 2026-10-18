package com.dlunaunizar.bobitos.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SpaceMoreScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val esEncabezado = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun tieneSusDosGruposConEncabezado() {
        val abiertos = mutableListOf<BobitosDestination>()
        compose.setContent { BobitosTheme { SpaceMoreScreen(onOpen = { abiertos += it }) } }

        compose.onNodeWithText("Del espacio").assert(esEncabezado)
        compose.onNodeWithText("Catálogos").assert(esEncabezado)
        compose.onNodeWithText("Recetario").performClick()

        assertEquals(listOf(BobitosDestination.Recipes), abiertos)
    }

    @Test
    fun lasFilasDeLosDosGruposMidenLoMismo() {
        compose.setContent { BobitosTheme { SpaceMoreScreen(onOpen = {}) } }

        val comidas = compose.onNodeWithText("Comidas").getUnclippedBoundsInRoot()
        val recetario = compose.onNodeWithText("Recetario").getUnclippedBoundsInRoot()

        val diferencia = kotlin.math.abs(
            (comidas.bottom - comidas.top).value - (recetario.bottom - recetario.top).value,
        )
        assertTrue("Comidas y Recetario difieren $diferencia dp", diferencia <= 1f)
    }
}
