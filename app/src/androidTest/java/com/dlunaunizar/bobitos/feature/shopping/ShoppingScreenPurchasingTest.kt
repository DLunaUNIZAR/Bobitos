package com.dlunaunizar.bobitos.feature.shopping

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.feature.common.FakeShopping
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ShoppingScreenPurchasingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun item(name: String) = ShoppingItem(
        id = name,
        name = name,
        quantity = null,
        notes = null,
        purchased = false,
        createdBy = "ana",
        createdByName = "Ana",
        createdAt = Instant.parse("2026-01-01T10:00:00Z"),
        updatedBy = "ana",
        updatedAt = Instant.parse("2026-01-01T10:00:00Z"),
        purchasedBy = null,
        purchasedByName = null,
        purchasedAt = null,
    )

    @Test
    fun comprarTresMuySeguidosEscribeLosTresAlMomento() {
        val fake = FakeShopping(listOf(item("pan"), item("leche"), item("huevos"), item("arroz")))
        val viewModel = fake.viewModel()
        compose.setContent { BobitosTheme { ShoppingScreen("space-1", canWrite = true, viewModel = viewModel) } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.onNode(hasContentDescription("pan")).performClick()
        compose.mainClock.advanceTimeBy(60)
        compose.onNode(hasContentDescription("leche")).performClick()
        compose.mainClock.advanceTimeBy(60)
        compose.onNode(hasContentDescription("huevos")).performClick()

        assertEquals(listOf("pan", "leche", "huevos"), fake.purchased)
        compose.onNodeWithText("pan").assertExists()
        compose.onNodeWithText("leche").assertExists()
        compose.onNodeWithText("huevos").assertExists()
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
    }
}
