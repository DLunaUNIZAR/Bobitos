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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.dlunaunizar.bobitos.core.model.ShoppingQuantity
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.findShoppingMatch
import com.dlunaunizar.bobitos.core.model.shoppingMatchKey
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository

/** Qué hace una fila de la revisión según la lista actual de la compra. */
enum class ReviewRowKind {
    /** No está en la lista: se añade. */
    NEW,

    /** Ya está pendiente: «Omitir» (por defecto) o «Sumar». */
    PENDING,

    /** Está, pero comprado: se vuelve a apuntar ese artículo (pendiente, con la cantidad nueva). */
    PURCHASED,
}

/**
 * Fila de la revisión de «añadir a la Compra» de los ingredientes de una receta o comida. Compartida
 * por Comidas y Recetario. `recipeQuantity` es la cantidad con su unidad («500 g»), ya sumada si el
 * ingrediente aparece varias veces; `quantities` lleva cada aportación por separado (con 2+ la
 * revisión las muestra todas).
 */
data class IngredientReviewRow(
    val name: String,
    val recipeQuantity: String?,
    val existing: ShoppingItem?,
    val quantities: List<String> = emptyList(),
    /** Solo en filas nuevas con ingrediente reconocido: da pie a elegir marca y tienda. */
    val context: IngredientShoppingContext? = null,
) {
    val kind: ReviewRowKind
        get() = when {
            existing == null -> ReviewRowKind.NEW
            existing.purchased -> ReviewRowKind.PURCHASED
            else -> ReviewRowKind.PENDING
        }
}

/** Lo que el usuario confirma en cada fila; `skip` = «Omitir» (no se escribe nada). */
data class IngredientReviewChoice(
    val quantity: String?,
    val supermarket: Supermarket?,
    val brand: String?,
    val skip: Boolean = false,
)

/** Estado editable de una fila en el diálogo; `include` = false solo en una fila pendiente con «Omitir». */
internal data class ReviewRowState(
    val quantity: String,
    val selection: BrandStoreSelection,
    val freeBrand: String,
    val include: Boolean = true,
)

// Pendiente: «Omitir» marcado y la suma preparada por si elige «Sumar». Nueva o comprada: la de la receta.
internal fun IngredientReviewRow.initialState(): ReviewRowState = when (kind) {
    ReviewRowKind.PENDING -> ReviewRowState(
        quantity = ShoppingQuantity.sum(existing?.quantity, recipeQuantity).orEmpty(),
        selection = BrandStoreSelection.None,
        freeBrand = "",
        include = false,
    )
    else -> ReviewRowState(
        quantity = recipeQuantity.orEmpty(),
        selection = context?.defaultSelection() ?: BrandStoreSelection.None,
        freeBrand = "",
    )
}

// Cada cambio parte del estado vigente de la fila, no de uno capturado en una composición anterior.
internal fun ReviewRowState.withBrand(context: IngredientShoppingContext, brandId: String?): ReviewRowState =
    copy(selection = context.selectBrand(selection, brandId))

internal fun ReviewRowState.withStore(store: Supermarket?): ReviewRowState =
    copy(selection = selection.copy(store = store))

internal fun ReviewRowState.withFreeBrand(text: String): ReviewRowState = copy(freeBrand = text)

internal fun ReviewRowState.withInclude(value: Boolean): ReviewRowState = copy(include = value)

/** La cantidad que se va a escribir supera el máximo: error en el campo y Confirmar deshabilitado. */
internal fun ReviewRowState.quantityTooLong(): Boolean = include && quantity.trim().length > ShoppingQuantity.MAX_LENGTH

internal fun ReviewRowState.toChoice(row: IngredientReviewRow): IngredientReviewChoice {
    val trimmed = quantity.trim().ifEmpty { null }
    val existing = row.existing
    val context = row.context
    return when {
        existing != null -> IngredientReviewChoice(trimmed, existing.supermarket, existing.brand, skip = !include)
        context == null -> IngredientReviewChoice(trimmed, null, null)
        else -> IngredientReviewChoice(
            quantity = trimmed,
            supermarket = selection.store,
            brand = context.brandToSave(selection, freeBrand),
        )
    }
}

/** Si al confirmar no se escribe nada (todas las filas en «Omitir»). */
fun List<IngredientReviewChoice>.writesNothing(): Boolean = all(IngredientReviewChoice::skip)

/**
 * Agrupa [ingredients] por [shoppingMatchKey] («Tomate» y «tomates» van juntos), suma sus cantidades y
 * los cruza con la lista de la compra actual ([currentItems]) con [findShoppingMatch] (manda el
 * pendiente). El orden respeta la primera aparición. Función pura (sin efectos), fácil de testear.
 */
fun buildIngredientReviewRows(
    ingredients: List<Ingredient>,
    currentItems: List<ShoppingItem>,
    data: IngredientShoppingData = IngredientShoppingData(),
): List<IngredientReviewRow> = ingredients.groupBy { shoppingMatchKey(it.name) }.values.map { occurrences ->
    val first = occurrences.first()
    val contributions = occurrences.mapNotNull(Ingredient::formattedQuantity)
    val existing = findShoppingMatch(currentItems, first.name)
    IngredientReviewRow(
        name = first.name,
        recipeQuantity = contributions.fold(null as String?) { total, next -> ShoppingQuantity.sum(total, next) },
        existing = existing,
        quantities = contributions,
        context = if (existing == null) data.contextFor(first.name) else null,
    )
}

/**
 * Aplica la revisión: «Omitir» no escribe; un pendiente con «Sumar» cambia solo su cantidad; un
 * comprado se vuelve a apuntar ([ShoppingRepository.relistItem]); los dos conservan notas, tienda y
 * marca. El resto se añade con la cantidad (que ya lleva la unidad) y el súper y la marca elegidos.
 */
suspend fun applyIngredientReview(
    spaceId: String,
    rows: List<IngredientReviewRow>,
    choices: List<IngredientReviewChoice>,
    shoppingRepository: ShoppingRepository,
) {
    rows.forEachIndexed { index, row ->
        val choice = choices.getOrNull(index)
        if (choice?.skip == true) return@forEachIndexed
        val quantity = choice?.quantity?.trim()?.takeIf(String::isNotEmpty)
        val existing = row.existing
        when {
            existing == null ->
                shoppingRepository.addItem(spaceId, row.name, quantity, null, choice?.supermarket, choice?.brand)
            existing.purchased -> shoppingRepository.relistItem(
                spaceId,
                existing.id,
                existing.name,
                quantity,
                existing.notes,
                existing.supermarket,
                existing.brand,
            )
            else -> shoppingRepository.updateItem(
                spaceId,
                existing.id,
                existing.name,
                quantity,
                existing.notes,
                existing.supermarket,
                existing.brand,
            )
        }
    }
}

// «200 g» / «2» / null si el ingrediente no trae cantidad. Une cantidad y unidad.
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
                    ReviewRowContent(row, states[index]) { change -> states[index] = change(states[index]) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(rows.mapIndexed { i, row -> states[i].toChoice(row) }) },
                enabled = states.none(ReviewRowState::quantityTooLong),
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ReviewRowContent(
    row: IngredientReviewRow,
    state: ReviewRowState,
    update: ((ReviewRowState) -> ReviewRowState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = row.name, style = MaterialTheme.typography.titleSmall)
        row.hint()?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.kind == ReviewRowKind.PENDING) {
            SkipOrSumPicker(include = state.include, onChange = { value -> update { it.withInclude(value) } })
        }
        if (state.include) {
            val tooLong = state.quantityTooLong()
            OutlinedTextField(
                value = state.quantity,
                onValueChange = { text -> update { it.copy(quantity = text) } },
                label = { Text(stringResource(R.string.recipes_ingredient_quantity)) },
                isError = tooLong,
                supportingText = if (tooLong) {
                    { Text(stringResource(R.string.shopping_error_quantity_too_long)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val context = row.context
        if (context != null) {
            BrandStorePicker(
                context = context,
                selection = state.selection,
                freeBrand = state.freeBrand,
                onBrandSelected = { brandId -> update { it.withBrand(context, brandId) } },
                onStoreSelected = { store -> update { it.withStore(store) } },
                onFreeBrandChange = { text -> update { it.withFreeBrand(text) } },
            )
        }
    }
}

@Composable
private fun IngredientReviewRow.hint(): String? = when {
    kind == ReviewRowKind.PENDING ->
        stringResource(R.string.meals_review_existing, existing?.quantity ?: "—", recipeQuantity ?: "—")
    kind == ReviewRowKind.PURCHASED -> stringResource(R.string.meals_review_purchased)
    quantities.size >= 2 -> stringResource(R.string.meals_review_multi, quantities.joinToString(" + "))
    recipeQuantity != null -> stringResource(R.string.meals_review_recipe, recipeQuantity)
    else -> null
}

// «Omitir» (no se toca el artículo de la lista) o «Sumar» (se edita la suma propuesta).
@Composable
private fun SkipOrSumPicker(include: Boolean, onChange: (Boolean) -> Unit) {
    val options = listOf(false to R.string.meals_review_skip, true to R.string.meals_review_sum)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = include == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(label))
            }
        }
    }
}
