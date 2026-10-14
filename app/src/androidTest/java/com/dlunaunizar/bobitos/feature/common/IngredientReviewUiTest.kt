package com.dlunaunizar.bobitos.feature.common

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.BobitosTheme
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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
                // 280 dp: el ancho mínimo del contenido de un AlertDialog.
                Box(Modifier.width(280.dp)) {
                    ReviewRowContent(row, row.initialState()) {}
                }
            }
        }

        assertEquals(1, lineCount(compose.activity.getString(R.string.shopping_brand_label)))
        assertEquals(1, lineCount(compose.activity.getString(R.string.shopping_supermarket_none)))
    }
}
