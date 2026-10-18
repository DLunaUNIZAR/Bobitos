package com.dlunaunizar.bobitos.feature.meals

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.feature.common.RecipeRow
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class RecipePickerRowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val recipe = Recipe(
        id = "r1",
        ownerUid = "u1",
        visibility = RecipeVisibility.PRIVATE,
        title = "Cocido",
        description = null,
        category = null,
        createdBy = "u1",
        createdByName = "Ana",
        createdAt = Instant.EPOCH,
        updatedBy = "u1",
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun cadaRecetaEsUnBoton() {
        compose.setContent { BobitosTheme { RecipePickerRow(row = RecipeRow(recipe, null), onClick = {}) } }

        compose.onNodeWithText("Cocido").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) and
                SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick),
        )
    }
}
