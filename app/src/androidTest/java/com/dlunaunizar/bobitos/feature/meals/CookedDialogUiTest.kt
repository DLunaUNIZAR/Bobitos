package com.dlunaunizar.bobitos.feature.meals

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class CookedDialogUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val review = CookedReview(
        mealName = "Lentejas",
        crossOff = listOf(
            ShoppingItem(
                id = "s1",
                name = "Lentejas",
                quantity = null,
                notes = null,
                purchased = false,
                createdBy = "u1",
                createdByName = "Ana",
                createdAt = Instant.EPOCH,
                updatedBy = "u1",
                updatedAt = Instant.EPOCH,
                purchasedBy = null,
                purchasedByName = null,
                purchasedAt = null,
            ),
        ),
        finished = emptyList(),
    )

    private fun setDialog(saving: Boolean, errorMessage: String?) {
        compose.setContent {
            BobitosTheme {
                CookedDialog(
                    review = review,
                    saving = saving,
                    errorMessage = errorMessage,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
    }

    private fun done() = compose.activity.getString(R.string.meals_cooked_done)

    @Test fun anErrorShowsInsideTheDialogAndDoneCanBeRetried() {
        setDialog(saving = false, errorMessage = "Sin conexión.")
        compose.onNodeWithText("Sin conexión.").assertIsDisplayed()
        compose.onNodeWithText(done()).assertIsEnabled()
    }

    @Test fun doneCannotBePressedWhileSaving() {
        setDialog(saving = true, errorMessage = null)
        compose.onNodeWithText(done()).assertIsNotEnabled()
    }

    @Test fun theScreenReaderAnnouncesTheError() {
        setDialog(saving = false, errorMessage = "Sin conexión.")
        compose.onNodeWithText("Sin conexión.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }
}
