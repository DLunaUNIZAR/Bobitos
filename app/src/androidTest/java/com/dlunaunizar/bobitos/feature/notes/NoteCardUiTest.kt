package com.dlunaunizar.bobitos.feature.notes

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.Note
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class NoteCardUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val note = Note(
        id = "n1",
        title = "Códigos del portal",
        body = "1234",
        pinned = false,
        createdBy = "u1",
        createdByName = "Ana",
        createdAt = Instant.EPOCH,
        updatedBy = "u1",
        updatedAt = Instant.EPOCH,
    )

    private fun show(onClick: (() -> Unit)?) = compose.setContent {
        BobitosTheme {
            NoteCard(
                note = note,
                enabled = true,
                onClick = onClick,
                onTogglePin = {},
                onEdit = {},
                onDelete = {},
            )
        }
    }

    @Test
    fun tappingTheCardOpensItsEditor() {
        var clicks = 0
        show { clicks++ }

        compose.onNodeWithText("Códigos del portal").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun withoutAClickTheCardIsNotClickable() {
        show(null)

        compose.onNodeWithText("Códigos del portal")
            .assert(SemanticsMatcher("no OnClick") { SemanticsActions.OnClick !in it.config })
    }
}
