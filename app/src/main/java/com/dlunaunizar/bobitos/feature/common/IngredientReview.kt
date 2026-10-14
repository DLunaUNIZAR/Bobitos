// Módulo pequeño compartido (fila + helpers + diálogo) en vez de un fichero por tipo.
@file:Suppress("MatchingDeclarationName")

package com.dlunaunizar.bobitos.feature.common

import androidx.annotation.StringRes
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
import com.dlunaunizar.bobitos.core.model.isInPantry
import com.dlunaunizar.bobitos.core.model.shoppingMatchKey
import com.dlunaunizar.bobitos.data.repository.ShoppingFailure
import com.dlunaunizar.bobitos.data.repository.ShoppingRepository
import com.dlunaunizar.bobitos.data.repository.ShoppingRepositoryException
import com.dlunaunizar.bobitos.data.repository.ShoppingWrite

/** Qué hace una fila de la revisión según la lista actual de la compra. */
enum class ReviewRowKind {
    /** No está en la lista: se añade. */
    NEW,

    /** Ya está pendiente: «Omitir» (por defecto) o «Sumar». */
    PENDING,

    /**
     * No está pendiente y hay en la despensa: «Omitir» (por defecto) o «Añadir», como una nueva o, si hay un comprado,
     * volviéndolo a apuntar.
     */
    IN_PANTRY,

    /** Está, pero comprado: «Omitir» (por defecto) o «Volver a apuntar» ese artículo (pendiente, con la cantidad nueva). */
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
    /** Hay en la despensa del espacio. */
    val inPantry: Boolean = false,
) {
    // Precedencia: pendiente en la lista → en la despensa → comprado → nueva.
    val kind: ReviewRowKind
        get() = when {
            existing != null && !existing.purchased -> ReviewRowKind.PENDING
            inPantry -> ReviewRowKind.IN_PANTRY
            existing != null -> ReviewRowKind.PURCHASED
            else -> ReviewRowKind.NEW
        }
}

/** Lo que el usuario confirma en cada fila; `skip` = «Omitir» (no se escribe nada). */
data class IngredientReviewChoice(
    val quantity: String?,
    val supermarket: Supermarket?,
    val brand: String?,
    val skip: Boolean = false,
)

/** Estado editable de una fila en el diálogo; `include` = false en una fila ya en la lista (pendiente o comprada) con «Omitir». */
internal data class ReviewRowState(
    val quantity: String,
    val selection: BrandStoreSelection,
    val freeBrand: String,
    val include: Boolean = true,
)

// Ya en la lista (pendiente o comprado): «Omitir» marcado; con «Sumar», la suma preparada; con «Volver a apuntar»,
// la cantidad de la receta. En la despensa: «Omitir» marcado, con la cantidad de la receta y la marca y la tienda
// por defecto. Nueva: la de la receta, con la marca y la tienda por defecto.
internal fun IngredientReviewRow.initialState(): ReviewRowState = when (kind) {
    ReviewRowKind.PENDING -> ReviewRowState(
        quantity = ShoppingQuantity.sum(existing?.quantity, recipeQuantity).orEmpty(),
        selection = BrandStoreSelection.None,
        freeBrand = "",
        include = false,
    )
    ReviewRowKind.PURCHASED -> ReviewRowState(
        quantity = recipeQuantity.orEmpty(),
        selection = BrandStoreSelection.None,
        freeBrand = "",
        include = false,
    )
    ReviewRowKind.IN_PANTRY -> ReviewRowState(
        quantity = recipeQuantity.orEmpty(),
        selection = context?.defaultSelection() ?: BrandStoreSelection.None,
        freeBrand = "",
        include = false,
    )
    ReviewRowKind.NEW -> ReviewRowState(
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

/** La marca libre de una fila que se va a añadir supera el máximo: error en el campo y Confirmar deshabilitado. */
internal fun ReviewRowState.brandTooLong(row: IngredientReviewRow): Boolean =
    include && row.context?.brands?.isEmpty() == true && freeBrandTooLong(freeBrand)

/** Confirmar se puede pulsar si ninguna fila tiene la cantidad o la marca libre demasiado larga. */
internal fun canConfirmReview(rows: List<IngredientReviewRow>, states: List<ReviewRowState>): Boolean =
    rows.indices.none { index -> states[index].quantityTooLong() || states[index].brandTooLong(rows[index]) }

internal fun ReviewRowState.toChoice(row: IngredientReviewRow): IngredientReviewChoice {
    val trimmed = quantity.trim().ifEmpty { null }
    val existing = row.existing
    val context = row.context
    return when {
        existing != null -> IngredientReviewChoice(trimmed, existing.supermarket, existing.brand, skip = !include)
        context == null -> IngredientReviewChoice(trimmed, null, null, skip = !include)
        else -> IngredientReviewChoice(
            quantity = trimmed,
            supermarket = selection.store,
            brand = context.brandToSave(selection, freeBrand),
            skip = !include,
        )
    }
}

/** Si al confirmar no se escribe nada (todas las filas en «Omitir»). */
fun List<IngredientReviewChoice>.writesNothing(): Boolean = all(IngredientReviewChoice::skip)

/**
 * Agrupa [ingredients] por [shoppingMatchKey] («Tomate» y «tomates» van juntos), suma sus cantidades y
 * los cruza con la lista de la compra actual ([currentItems]) con [findShoppingMatch] (manda el
 * pendiente) y con la despensa ([pantryIds], leída del servidor): pendiente → en la despensa → comprado → nueva.
 * El orden respeta la primera aparición. Función pura (sin efectos), fácil de testear.
 */
fun buildIngredientReviewRows(
    ingredients: List<Ingredient>,
    currentItems: List<ShoppingItem>,
    data: IngredientShoppingData = IngredientShoppingData(),
    pantryIds: Set<String> = emptySet(),
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
        inPantry = isInPantry(first.name, pantryIds),
    )
}

/**
 * Aplica la revisión en una sola escritura ([ShoppingRepository.applyWrites]); ver [ingredientReviewWrites].
 * Si otro miembro ha borrado un artículo desde que se abrió la revisión, falla con [ShoppingFailure.ItemChanged]
 * (la lista ha cambiado y reintentar lo arregla), no con [ShoppingFailure.ItemNotFound].
 */
suspend fun applyIngredientReview(
    spaceId: String,
    rows: List<IngredientReviewRow>,
    choices: List<IngredientReviewChoice>,
    shoppingRepository: ShoppingRepository,
) {
    val writes = ingredientReviewWrites(rows, choices)
    if (writes.isEmpty()) return
    try {
        shoppingRepository.applyWrites(spaceId, writes)
    } catch (error: ShoppingRepositoryException) {
        if (error.failure == ShoppingFailure.ItemNotFound) {
            throw ShoppingRepositoryException(ShoppingFailure.ItemChanged, error)
        }
        throw error
    }
}

/**
 * Lo que escribe la revisión, en el orden de las filas: «Omitir» no escribe; un pendiente con «Sumar» cambia solo su
 * cantidad; un comprado se vuelve a apuntar; los dos conservan notas, tienda y marca y van con el artículo tal como
 * se vio. El resto se añade con la cantidad (que ya lleva la unidad) y el súper y la marca elegidos.
 */
fun ingredientReviewWrites(
    rows: List<IngredientReviewRow>,
    choices: List<IngredientReviewChoice>,
): List<ShoppingWrite> = rows.mapIndexedNotNull { index, row ->
    val choice = choices.getOrNull(index)
    if (choice?.skip == true) return@mapIndexedNotNull null
    val quantity = choice?.quantity?.trim()?.takeIf(String::isNotEmpty)
    val existing = row.existing
    if (existing == null) {
        ShoppingWrite.Add(row.name, quantity, null, choice?.supermarket, choice?.brand)
    } else {
        val relist = existing.purchased
        ShoppingWrite.Change(existing, quantity, existing.notes, existing.supermarket, existing.brand, relist)
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
                enabled = canConfirmReview(rows, states),
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

// El spread copia un array de 0-2 textos: no importa.
@Suppress("SpreadOperator")
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
                text = stringResource(hint.res, *hint.args.toTypedArray()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.kind != ReviewRowKind.NEW) {
            SkipOrIncludePicker(
                include = state.include,
                includeLabel = row.includeLabel(),
                onChange = { value -> update { it.withInclude(value) } },
            )
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
        if (context != null && state.include) {
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

/** Pista bajo el nombre de una fila: su texto y sus argumentos. Si falta una cantidad, no se nombra (sin «—»). */
internal data class ReviewHint(@StringRes val res: Int, val args: List<String> = emptyList())

/** La opción que no omite: «Sumar» en un pendiente; «Volver a apuntar» si hay un comprado; si no, «Añadir». */
@StringRes
internal fun IngredientReviewRow.includeLabel(): Int = when {
    kind == ReviewRowKind.PENDING -> R.string.meals_review_sum
    existing != null -> R.string.meals_review_relist
    else -> R.string.meals_review_add
}

internal fun IngredientReviewRow.hint(): ReviewHint? = when (kind) {
    ReviewRowKind.PENDING -> pendingHint()
    ReviewRowKind.IN_PANTRY -> recipeQuantity?.let { ReviewHint(R.string.meals_review_in_pantry_recipe, listOf(it)) }
        ?: ReviewHint(R.string.meals_review_in_pantry)
    ReviewRowKind.PURCHASED -> recipeQuantity?.let { ReviewHint(R.string.meals_review_purchased_recipe, listOf(it)) }
        ?: ReviewHint(R.string.meals_review_purchased)
    ReviewRowKind.NEW -> when {
        quantities.size >= 2 -> ReviewHint(R.string.meals_review_multi, listOf(quantities.joinToString(" + ")))
        recipeQuantity != null -> ReviewHint(R.string.meals_review_recipe, listOf(recipeQuantity))
        else -> null
    }
}

private fun IngredientReviewRow.pendingHint(): ReviewHint {
    val listed = existing?.quantity?.trim()?.takeIf(String::isNotEmpty)
    val recipe = recipeQuantity
    return when {
        listed != null && recipe != null -> ReviewHint(R.string.meals_review_existing, listOf(listed, recipe))
        listed != null -> ReviewHint(R.string.meals_review_existing_listed, listOf(listed))
        recipe != null -> ReviewHint(R.string.meals_review_existing_recipe, listOf(recipe))
        else -> ReviewHint(R.string.meals_review_existing_plain)
    }
}

// «Omitir» (no se toca el artículo de la lista) o «Sumar» / «Volver a apuntar» (se edita la cantidad propuesta).
@Composable
private fun SkipOrIncludePicker(include: Boolean, @StringRes includeLabel: Int, onChange: (Boolean) -> Unit) {
    val options = listOf(false to R.string.meals_review_skip, true to includeLabel)
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
