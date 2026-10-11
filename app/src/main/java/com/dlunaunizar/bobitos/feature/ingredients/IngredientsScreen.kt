package com.dlunaunizar.bobitos.feature.ingredients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosTopBar
import com.dlunaunizar.bobitos.core.designsystem.component.EditorSaveEffect
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.InfoChip
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.feature.shopping.SupermarketIcon
import com.dlunaunizar.bobitos.feature.shopping.labelRes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientsScreen(
    onBack: () -> Unit,
    onOpenIngredient: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IngredientsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        viewModel.observe()
        onDispose { viewModel.stopObserving() }
    }
    // Crear a mano sobrevive a una rotación; el flujo desde escaneo (scanCreate) lleva la nutrición y no.
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var scanCreate by remember { mutableStateOf<ScannedProduct?>(null) }
    var createCommon by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { viewModel.setQuery(query) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Un alta duplicada lleva a la ficha existente.
    LaunchedEffect(state.openIngredientId) {
        state.openIngredientId?.let {
            onOpenIngredient(it)
            viewModel.consumeOpenIngredient()
        }
    }

    // Un escaneo abre el editor de «nuevo ingrediente» prerrellenado con el producto.
    LaunchedEffect(state.scannedProduct) {
        state.scannedProduct?.let {
            scanCreate = it
            viewModel.consumeScannedProduct()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            BobitosTopBar(
                title = stringResource(R.string.ingredients_title),
                onBack = onBack,
                actions = {
                    IconButton(
                        enabled = !state.isLookingUp,
                        onClick = { scope.launch { scanBarcode(context)?.let(viewModel::lookupBarcode) } },
                    ) {
                        Icon(
                            Icons.Rounded.QrCodeScanner,
                            contentDescription = stringResource(R.string.ingredients_brand_scan),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showEditor = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.ingredients_add)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            IngredientsFeedback(
                state.error,
                state.notice,
                state.isSaving,
                viewModel::clearFeedback,
                existingName = state.existingName,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.ingredients_search_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
            IngredientCatalog(state, onOpen = { onOpenIngredient(it.id) }, onRetry = viewModel::retryCatalog)
        }
    }

    EditorSaveEffect(
        status = state.editorSave,
        editorOpen = showEditor || scanCreate != null,
        onClose = {
            showEditor = false
            scanCreate = null
        },
        onConsume = viewModel::consumeEditorSave,
    )
    val editorError = editorErrorMessage(state.error, state.editorSave, state.existingName)

    if (showEditor) {
        IngredientEditorDialog(
            ingredient = null,
            saving = state.isSaving,
            errorMessage = editorError,
            onDismiss = { showEditor = false },
            onSave = { name, category, unit -> viewModel.createIngredient(name, category, unit, createCommon) },
            commonChecked = createCommon.takeIf { state.isAdmin },
            onCommonChange = if (state.isAdmin) ({ createCommon = it }) else null,
        )
    }

    scanCreate?.let { product ->
        IngredientEditorDialog(
            ingredient = null,
            initialName = product.suggestedName,
            saving = state.isSaving,
            errorMessage = editorError,
            onDismiss = { scanCreate = null },
            onSave = { name, category, unit ->
                viewModel.createIngredientFromScan(
                    name,
                    category,
                    unit,
                    product.brandName,
                    product.barcode,
                    product.nutrition,
                )
            },
        )
    }
}

@Composable
private fun IngredientCatalog(state: IngredientsUiState, onOpen: (CatalogIngredient) -> Unit, onRetry: () -> Unit) {
    when (val catalog = state.ingredients) {
        UiState.Loading -> LoadingState(Modifier.fillMaxWidth())
        is UiState.Error -> ErrorState(
            Modifier.fillMaxWidth(),
            message = state.catalogError?.let { stringResource(it.stringResourceId) } ?: catalog.message,
            onRetry = onRetry,
        )
        is UiState.Content -> {
            val filtered = catalog.value.matching(state.query)
            val brandsByIngredient = remember(state.brands) { state.brands.groupBy { it.ingredientId } }
            if (filtered.isEmpty()) {
                EmptyState(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Kitchen,
                    title = stringResource(
                        if (state.query.isNotBlank() && catalog.value.isNotEmpty()) {
                            R.string.ingredients_no_results
                        } else {
                            R.string.ingredients_empty
                        },
                    ),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(filtered, key = CatalogIngredient::id) { ingredient ->
                        IngredientRow(
                            ingredient = ingredient,
                            line = ingredientRowLine(
                                brandsByIngredient[ingredient.id].orEmpty(),
                                state.prefs[ingredient.id],
                            ),
                            onClick = { onOpen(ingredient) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IngredientRow(
    ingredient: CatalogIngredient,
    line: IngredientRowLine,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ingredient.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ingredient.category?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IngredientLine(line)
            }
            if (ingredient.personal) InfoChip(stringResource(R.string.ingredients_personal))
        }
    }
}

@Composable
private fun IngredientLine(line: IngredientRowLine) {
    when (line) {
        is IngredientRowLine.Favorite -> StoreLine(
            text = stringResource(R.string.ingredients_favorite_brand, line.brand),
            store = line.store,
        )
        is IngredientRowLine.Brands -> StoreLine(
            text = pluralStringResource(R.plurals.ingredients_brand_count, line.count, line.count),
            store = null,
        )
        is IngredientRowLine.Store -> StoreLine(text = null, store = line.store)
        IngredientRowLine.None -> Unit
    }
}

@Composable
private fun StoreLine(text: String?, store: Supermarket?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        text?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (store != null) {
            SupermarketIcon(store)
            Text(
                text = stringResource(store.labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun IngredientEditorDialog(
    ingredient: CatalogIngredient?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?) -> Unit,
    initialName: String = "",
    errorMessage: String? = null,
    // Interruptor «Común (catálogo)»: solo se pasa al admin al crear.
    commonChecked: Boolean? = null,
    onCommonChange: ((Boolean) -> Unit)? = null,
) {
    val initial = CatalogIngredientDraft.of(ingredient, initialName)
    var draft by rememberSaveable(ingredient?.id, initialName) {
        mutableStateOf(initial)
    }
    BobitosFormSheet(
        title = stringResource(
            if (ingredient == null) R.string.ingredients_add_title else R.string.ingredients_edit_title,
        ),
        confirmLabel = stringResource(R.string.save),
        confirmEnabled = draft.name.isNotBlank(),
        saving = saving,
        dirty = { draft != initial },
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = {
            onSave(draft.name, draft.category.trim().ifBlank { null }, draft.unit.trim().ifBlank { null })
        },
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it) },
            label = { Text(stringResource(R.string.ingredients_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.category,
            onValueChange = { draft = draft.copy(category = it) },
            label = { Text(stringResource(R.string.ingredients_category_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.unit,
            onValueChange = { draft = draft.copy(unit = it) },
            label = { Text(stringResource(R.string.ingredients_unit_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (commonChecked != null && onCommonChange != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = commonChecked, role = Role.Switch, onValueChange = onCommonChange),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.ingredients_common_switch), modifier = Modifier.weight(1f))
                Switch(checked = commonChecked, onCheckedChange = null)
            }
        }
    }
}

@Composable
internal fun IngredientsFeedback(
    error: IngredientUiMessage?,
    notice: IngredientUiMessage?,
    saving: Boolean,
    onDismiss: () -> Unit,
    existingName: String? = null,
) {
    val message = error ?: notice
    if (message == null && !saving) return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        color = if (error != null) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Row(modifier = Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (saving) {
                    stringResource(
                        R.string.write_saving,
                    )
                } else {
                    ingredientMessageText(message!!, existingName)
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!saving) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
            }
        }
    }
}

/** El error del guardado del editor abierto (solo con FAILED); los demás se ven en el banner. */
@Composable
internal fun editorErrorMessage(
    error: IngredientUiMessage?,
    editorSave: EditorSaveStatus,
    existingName: String? = null,
): String? = error?.takeIf { editorSave == EditorSaveStatus.FAILED }?.let { ingredientMessageText(it, existingName) }

/** Texto del mensaje; «ya existe» nombra la ficha encontrada si se conoce. */
@Composable
private fun ingredientMessageText(message: IngredientUiMessage, existingName: String?): String =
    if (message == IngredientUiMessage.AlreadyExists && existingName != null) {
        stringResource(R.string.ingredients_error_already_exists, existingName)
    } else {
        stringResource(message.stringResourceId)
    }
