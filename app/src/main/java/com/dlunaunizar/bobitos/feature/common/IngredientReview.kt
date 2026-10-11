// Módulo pequeño compartido (fila + helpers + diálogo) en vez de un fichero por tipo.
@file:Suppress("MatchingDeclarationName")

package com.dlunaunizar.bobitos.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository

/**
 * Fila de la revisión de «añadir a la Compra» de los ingredientes de una receta o comida. Compartida
 * por Comidas y Recetario. `quantities` lleva TODAS las aportaciones (para no perder cantidades cuando
 * un ingrediente aparece en varias comidas); con 2+ la revisión las muestra todas.
 */
data class IngredientReviewRow(
    val name: String,
    val unit: String?,
    val recipeQuantity: String?,
    val existing: ShoppingItem?,
    val quantities: List<String> = emptyList(),
    /** Solo en filas nuevas con ingrediente reconocido: da pie a elegir marca y tienda. */
    val context: IngredientShoppingContext? = null,
)

/** Lo que el usuario confirma en cada fila. */
data class IngredientReviewChoice(val quantity: String?, val supermarket: Supermarket?, val brand: String?)

/** Estado editable de una fila en el diálogo. */
internal data class ReviewRowState(val quantity: String, val selection: BrandStoreSelection, val freeBrand: String)

internal fun IngredientReviewRow.initialState(): ReviewRowState = ReviewRowState(
    quantity = (existing?.quantity ?: recipeQuantity).orEmpty(),
    selection = context?.defaultSelection() ?: BrandStoreSelection.None,
    freeBrand = "",
)

// Cada cambio parte del estado vigente de la fila, no de uno capturado en una composición anterior.
internal fun ReviewRowState.withBrand(context: IngredientShoppingContext, brandId: String?): ReviewRowState =
    copy(selection = context.selectBrand(selection, brandId))

internal fun ReviewRowState.withStore(store: Supermarket?): ReviewRowState =
    copy(selection = selection.copy(store = store))

internal fun ReviewRowState.withFreeBrand(text: String): ReviewRowState = copy(freeBrand = text)

internal fun ReviewRowState.toChoice(row: IngredientReviewRow): IngredientReviewChoice {
    val trimmed = quantity.trim().ifEmpty { null }
    val existing = row.existing
    val context = row.context
    return when {
        existing != null -> IngredientReviewChoice(trimmed, existing.supermarket, existing.brand)
        context == null -> IngredientReviewChoice(trimmed, null, null)
        else -> IngredientReviewChoice(
            quantity = trimmed,
            supermarket = selection.store,
            brand = context.brandToSave(selection, freeBrand),
        )
    }
}

/**
 * Agrupa [ingredients] por nombre (conservando todas las apariciones) y los cruza con la lista de la
 * compra actual ([currentItems]) para detectar los que ya existen. El orden respeta la primera
 * aparición. Función pura (sin efectos), fácil de testear.
 */
fun buildIngredientReviewRows(
    ingredients: List<Ingredient>,
    currentItems: List<ShoppingItem>,
    data: IngredientShoppingData = IngredientShoppingData(),
): List<IngredientReviewRow> {
    val byName = currentItems.associateBy { it.name.trim().lowercase() }
    return ingredients.groupBy { it.name.trim().lowercase() }.map { (key, occurrences) ->
        val first = occurrences.first()
        val contributions = occurrences.mapNotNull(Ingredient::formattedQuantity)
        val single = contributions.size <= 1
        val existing = byName[key]
        IngredientReviewRow(
            name = first.name,
            // Con varias aportaciones, la unidad ya va dentro de cada cantidad formateada.
            unit = if (single) first.unit else null,
            recipeQuantity = if (single) first.quantity else contributions.joinToString(" + "),
            existing = existing,
            quantities = contributions,
            context = if (existing == null) data.contextFor(first.name) else null,
        )
    }
}

/**
 * Aplica la revisión: actualiza solo la cantidad de los ítems ya existentes (conservan notas, súper y
 * marca) y añade el resto con el supermercado y la marca elegidos en [choices].
 */
suspend fun applyIngredientReview(
    spaceId: String,
    rows: List<IngredientReviewRow>,
    choices: List<IngredientReviewChoice>,
    shoppingRepository: ShoppingRepository,
) {
    rows.forEachIndexed { index, row ->
        val choice = choices.getOrNull(index)
        val quantity = choice?.quantity?.trim()?.takeIf(String::isNotEmpty)
        val existing = row.existing
        if (existing != null) {
            shoppingRepository.updateItem(
                spaceId,
                existing.id,
                existing.name,
                quantity,
                existing.notes,
                existing.supermarket,
                existing.brand,
            )
        } else {
            shoppingRepository.addItem(spaceId, row.name, quantity, row.unit, choice?.supermarket, choice?.brand)
        }
    }
}

// «200 g» / «2» / null si el ingrediente no trae cantidad. Une cantidad y unidad para la revisión.
private fun Ingredient.formattedQuantity(): String? {
    val q = quantity?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return listOfNotNull(q, unit?.trim()?.takeIf(String::isNotEmpty)).joinToString(" ")
}

@Composable
fun IngredientReviewDialog(
    rows: List<IngredientReviewRow>,
    onConfirm: (List<IngredientReviewChoice>) -> Unit,
    onDismiss: () -> Unit,
) {
    val states = remember(rows) { rows.map { it.initialState() }.toMutableStateList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.meals_review_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rows.forEachIndexed { index, row ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = row.name, style = MaterialTheme.typography.titleSmall)
                        val hint = when {
                            row.existing != null -> stringResource(
                                R.string.meals_review_existing,
                                row.existing.quantity ?: "—",
                                row.recipeQuantity ?: "—",
                            )
                            row.quantities.size >= 2 ->
                                stringResource(R.string.meals_review_multi, row.quantities.joinToString(" + "))
                            row.recipeQuantity != null ->
                                stringResource(R.string.meals_review_recipe, row.recipeQuantity)
                            else -> null
                        }
                        if (hint != null) {
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = states[index].quantity,
                            onValueChange = { states[index] = states[index].copy(quantity = it) },
                            label = { Text(stringResource(R.string.recipes_ingredient_quantity)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val context = row.context
                        if (context != null) {
                            BrandStorePicker(
                                context = context,
                                selection = states[index].selection,
                                freeBrand = states[index].freeBrand,
                                onBrandSelected = { states[index] = states[index].withBrand(context, it) },
                                onStoreSelected = { states[index] = states[index].withStore(it) },
                                onFreeBrandChange = { states[index] = states[index].withFreeBrand(it) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(rows.mapIndexed { i, row -> states[i].toChoice(row) }) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
