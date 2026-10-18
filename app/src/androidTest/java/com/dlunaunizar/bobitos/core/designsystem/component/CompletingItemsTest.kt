package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CompletingItemsTest {
    @get:Rule
    val compose = createComposeRule()

    private var pendientes by mutableStateOf(listOf("pan", "leche", "huevos", "arroz"))
    private val escritas = mutableListOf<String>()

    private fun mostrar() {
        compose.setContent {
            val completing = rememberCompletingItems()
            Column {
                // Como Firestore: la escritura quita el elemento de la lista al momento.
                completing.retain(pendientes) { it }.forEach { id ->
                    Column(Modifier.completingExit(completing, id)) {
                        Checkbox(
                            checked = completing.isCompleting(id),
                            onCheckedChange = {
                                completing.start(id)
                                escritas += id
                                pendientes = pendientes - id
                            },
                            modifier = Modifier.semantics { contentDescription = id },
                        )
                        Text(id)
                    }
                }
            }
        }
    }

    @Test
    fun marcarTresMuySeguidosEscribeLasTresAlMomento() {
        compose.mainClock.autoAdvance = false
        mostrar()

        compose.onNode(hasContentDescription("pan")).performClick()
        compose.mainClock.advanceTimeBy(60)
        compose.onNode(hasContentDescription("leche")).performClick()
        compose.mainClock.advanceTimeBy(60)
        compose.onNode(hasContentDescription("huevos")).performClick()

        // Las tres escrituras salen antes de que el reloj llegue a los 450 ms.
        assertEquals(listOf("pan", "leche", "huevos"), escritas)
        // Aunque la lista ya no las tenga, las filas siguen ahí, marcadas.
        compose.onNodeWithText("pan").assertExists()
        compose.onNodeWithText("leche").assertExists()
        compose.onNodeWithText("huevos").assertExists()

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_500)
        compose.onAllNodesWithText("pan").assertCountEquals(0)
        compose.onAllNodesWithText("leche").assertCountEquals(0)
        compose.onAllNodesWithText("huevos").assertCountEquals(0)
        compose.onNodeWithText("arroz").assertExists()
    }
}
