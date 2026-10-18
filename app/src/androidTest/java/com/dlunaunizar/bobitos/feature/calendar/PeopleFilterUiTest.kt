package com.dlunaunizar.bobitos.feature.calendar

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import org.junit.Rule
import org.junit.Test

class PeopleFilterUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val miembros = listOf(
        SpaceMember("a", "Ana", SpaceRole.OWNER),
        SpaceMember("b", "Luis", SpaceRole.MEMBER),
        SpaceMember("c", "Marta", SpaceRole.MEMBER),
    )
    private var elegidas by mutableStateOf(setOf("a", "b", "c"))

    private fun pintar() = compose.setContent {
        BobitosTheme {
            PeopleFilter(
                members = miembros,
                selectedIds = elegidas,
                onToggle = { id -> elegidas = if (id in elegidas) elegidas - id else elegidas + id },
                onSelectAll = { elegidas = miembros.map { it.userId }.toSet() },
            )
        }
    }

    @Test
    fun elegirDosPersonasDejaElMenuAbiertoYDiceDosPersonas() {
        elegidas = emptySet()
        pintar()
        compose.onNodeWithTag("filtro_personas").performClick()
        compose.onNodeWithText("Ana").performClick()
        compose.onNodeWithText("Luis").performClick()
        compose.waitForIdle()
        // El menú sigue abierto: la opción «Marta» todavía se ve.
        compose.onNodeWithText("Marta").assertExists()
        compose.onNodeWithTag("filtro_personas").assertTextContains("2 personas", substring = true)
    }

    @Test
    fun todasVuelveATodas() {
        elegidas = setOf("a")
        pintar()
        compose.onNodeWithTag("filtro_personas").performClick()
        compose.onNodeWithText("Todas").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("filtro_personas").assertTextContains("Todas", substring = true)
    }
}
