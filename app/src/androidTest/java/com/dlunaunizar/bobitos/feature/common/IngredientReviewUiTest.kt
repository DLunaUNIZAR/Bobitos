package com.dlunaunizar.bobitos.feature.common

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class IngredientReviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    // Una fila nueva de un ingrediente reconocido sin marcas registradas: marca libre y tienda «Sin especificar».
    private val row = IngredientReviewRow(
        name = "Arroz",
        recipeQuantity = "300 g",
        existing = null,
        quantities = listOf("300 g"),
        context = IngredientShoppingContext(
            ingredient = CatalogIngredient("arroz", "Arroz"),
            brands = emptyList(),
            stores = emptyList(),
        ),
    )

    // Líneas que ocupa [text] en su propio Text (sin fusionar con el campo o el botón que lo contiene).
    private fun lineCount(text: String): Int {
        val node = compose.onAllNodesWithText(text, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first { SemanticsActions.GetTextLayoutResult in it.config }
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assertEquals(text, layout.layoutInput.text.text)
        return layout.lineCount
    }

    @Test fun brandAndStoreFitOnOneLineInTheNarrowestReview() {
        compose.setContent {
            BobitosTheme {
                // 232 dp: los 280 dp mínimos de un AlertDialog menos sus 24 dp de margen a cada lado.
                Box(Modifier.width(232.dp)) {
                    ReviewRowContent(row, row.initialState()) {}
                }
            }
        }

        assertEquals(1, lineCount(compose.activity.getString(R.string.shopping_brand_label)))
        assertEquals(1, lineCount(compose.activity.getString(R.string.shopping_supermarket_none)))
    }

    private fun item(quantity: String?, purchased: Boolean = false) = ShoppingItem(
        id = "s1",
        name = "Arroz",
        quantity = quantity,
        notes = null,
        purchased = purchased,
        createdBy = "u",
        createdByName = "U",
        createdAt = Instant.EPOCH,
        updatedBy = "u",
        updatedAt = Instant.EPOCH,
        purchasedBy = null,
        purchasedByName = null,
        purchasedAt = null,
        supermarket = null,
        brand = null,
    )

    // Los Text propios (sin fusionar) con [text], de todas las filas.
    private fun textNodes(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true)
        .filter(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult))

    private fun layoutsOf(text: String): List<TextLayoutResult> {
        val nodes = textNodes(text).fetchSemanticsNodes()
        assertTrue(text, nodes.isNotEmpty())
        return nodes.map { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            layouts.single()
        }
    }

    @Test fun aNewRowOffersNoAnadirAndHidesItsFieldsWhenChosen() {
        compose.setContent {
            BobitosTheme {
                var state by remember { mutableStateOf(row.initialState()) }
                ReviewRowContent(row, state) { change -> state = change(state) }
            }
        }
        val skip = compose.activity.getString(R.string.meals_review_skip_description)
        val quantity = compose.activity.getString(R.string.recipes_ingredient_quantity)

        compose.onNodeWithContentDescription(skip).assertIsNotSelected()
        compose.onNodeWithText(compose.activity.getString(R.string.meals_review_add)).assertIsSelected()
        compose.onNodeWithText(quantity).assertIsDisplayed()

        compose.onNodeWithContentDescription(skip).performClick()

        compose.onNodeWithContentDescription(skip).assertIsSelected()
        compose.onNodeWithText(quantity).assertDoesNotExist()
    }

    @Test fun aRowInThePantryAndInTheListShowsBothBadges() {
        val pending = row.copy(existing = item("1 kg"), inPantry = true, context = null)
        compose.setContent {
            BobitosTheme {
                var state by remember { mutableStateOf(pending.initialState()) }
                ReviewRowContent(pending, state) { change -> state = change(state) }
            }
        }
        val pantry = compose.onNodeWithText(compose.activity.getString(R.string.meals_review_badge_pantry))
        val listed = compose.onNodeWithText(compose.activity.getString(R.string.meals_review_badge_listed, "1 kg"))

        pantry.assertIsDisplayed().assertHasNoClickAction()
        listed.assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.meals_review_skip_description))
            .assertIsSelected()
        compose.onNodeWithText(compose.activity.getString(R.string.meals_review_sum)).assertExists()
    }

    @Test fun badgesAndPickerAreNotCutAtLargeFontInTheNarrowestReview() {
        val bought = row.copy(
            existing = item("1 kg", purchased = true),
            inPantry = true,
            quantities = listOf("500 g", "2 ud", "1 kg"),
            context = null,
        )
        val long = "500 g + 2 ud + 1 kg"
        val pending = row.copy(name = "Pasta", existing = item(long), context = null)
        compose.setContent {
            BobitosTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f),
                ) {
                    Box(Modifier.width(232.dp).testTag("review")) {
                        Column {
                            ReviewRowContent(bought, bought.initialState()) {}
                            ReviewRowContent(pending, pending.initialState()) {}
                        }
                    }
                }
            }
        }
        val picker = listOf(
            compose.activity.getString(R.string.meals_review_skip),
            compose.activity.getString(R.string.meals_review_relist),
            compose.activity.getString(R.string.meals_review_sum),
        )
        val badges = listOf(
            compose.activity.getString(R.string.meals_review_badge_pantry),
            compose.activity.getString(R.string.meals_review_badge_purchased),
        )
        val listed = compose.activity.getString(R.string.meals_review_badge_listed, long)
        val box = compose.onNodeWithTag("review").getUnclippedBoundsInRoot()

        // El selector y las etiquetas cortas van en una línea; la larga salta de línea y se ve entera.
        // didOverflowWidth no sirve: un texto que no llena su ancho máximo también lo marca.
        assertEquals(2, layoutsOf(picker[0]).size)
        // «Volver a apuntar» ya salta de línea (2) a 232 dp y 130 %: entero, no cortado.
        (picker + badges).forEach { text ->
            val expected = if (text == picker[1]) 2 else 1
            layoutsOf(text).forEach { assertEquals(text, expected, it.lineCount) }
        }
        assertTrue(listed, layoutsOf(listed).single().lineCount > 1)
        // Ningún texto sobresale de la caja ni se corta por alto.
        (picker + badges + listed).forEach { text ->
            layoutsOf(text).forEach { assertFalse(text, it.didOverflowHeight) }
            val count = textNodes(text).fetchSemanticsNodes().size
            for (i in 0 until count) {
                val bounds = textNodes(text)[i].getUnclippedBoundsInRoot()
                assertTrue("$text $bounds en $box", bounds.left >= box.left && bounds.right <= box.right)
                assertTrue("$text $bounds en $box", bounds.top >= box.top && bounds.bottom <= box.bottom)
            }
        }
    }
}
