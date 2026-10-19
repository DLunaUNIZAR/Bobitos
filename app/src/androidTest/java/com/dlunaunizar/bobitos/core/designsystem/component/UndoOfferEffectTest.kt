package com.dlunaunizar.bobitos.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.UndoOffer
import com.dlunaunizar.bobitos.core.common.UndoOfferControls
import com.dlunaunizar.bobitos.core.common.Undoable
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UndoOfferEffectTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val offers = mutableStateListOf<UndoOffer<String>>()
    private val consumed = mutableListOf<Long>()
    private val undone = mutableListOf<Undoable<String>>()
    private val host = SnackbarHostState()
    private val controls = object : UndoOfferControls {
        override fun consumeOffer(serial: Long) {
            consumed += serial
            offers.removeAll { it.serial == serial }
        }
    }

    private fun show() {
        compose.setContent {
            BobitosTheme {
                CompositionLocalProvider(LocalSnackbarHostState provides host) {
                    Scaffold(snackbarHost = { SnackbarHost(host) }) { padding ->
                        Box(Modifier.padding(padding))
                        UndoOfferEffect(
                            offers = offers,
                            controls = controls,
                            deletedMessage = R.string.tasks_undo_deleted,
                            onUndo = { undone += it },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun aPendingOfferShowsItsMessageOnceAndIsConsumed() {
        offers += UndoOffer(1L, Undoable.Deleted("s", "Tarea"))
        show()

        compose.onNodeWithText("Tarea eliminada").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(1L), consumed) }
    }

    @Test
    fun aCreatedOfferNamesWhatWasAdded() {
        offers += UndoOffer(1L, Undoable.Created("s", CreatedItem("1", "Pan")))
        show()

        compose.onNodeWithText("Has añadido «Pan»").assertIsDisplayed()
    }

    @Test
    fun pressingDeshacerGivesBackThatUndoable() {
        val deleted = Undoable.Deleted("s", "Tarea")
        offers += UndoOffer(1L, deleted)
        show()

        compose.onNodeWithText("Deshacer").performClick()

        compose.runOnIdle { assertEquals(listOf<Undoable<String>>(deleted), undone) }
    }

    @Test
    fun twoOffersShowOneAfterTheOtherAndEachUndoesItsOwn() {
        val first = Undoable.Deleted("s", "Primera")
        val second = Undoable.Created("s", CreatedItem("2", "Pan"))
        offers += UndoOffer(1L, first)
        offers += UndoOffer(2L, second)
        show()

        compose.onNodeWithText("Tarea eliminada").assertIsDisplayed()
        compose.onNodeWithText("Deshacer").performClick()
        compose.onNodeWithText("Has añadido «Pan»").assertIsDisplayed()
        compose.onNodeWithText("Deshacer").performClick()

        compose.runOnIdle {
            assertEquals(listOf(1L, 2L), consumed)
            assertEquals(listOf<Undoable<String>>(first, second), undone)
        }
    }
}
