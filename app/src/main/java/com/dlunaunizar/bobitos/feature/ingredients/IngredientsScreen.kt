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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.messageRes
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.designsystem.component.EditorSaveEffect
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.InfoChip
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.component.ScreenScaffold
import com.dlunaunizar.bobitos.core.designsystem.component.SearchField
import com.dlunaunizar.bobitos.core.designsystem.component.SwitchRow
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
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
    onOpenBrand: (ingredientId: String, brandId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IngredientsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        viewModel.observe()
        onDispose { viewModel.stopObserving() }
    }
    // Crear a mano sobrevive a una rotación; el resto del flujo de escaneo vive en el ViewModel.
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showScanCreate by rememberSaveable { mutableStateOf(false) }
    var createCommon by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    IngredientsEvents(state, viewModel, onOpenIngredient, onOpenBrand) { showScanCreate = false }

    ScreenScaffold(
        title = stringResource(R.string.ingredients_title),
        onBack = onBack,
        modifier = modifier,
        actions = { ScanButton(enabled = !state.isLookingUp, onScanned = viewModel::lookupBarcode) },
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
            SearchField(
                query = query,
                onQueryChange = { query = it },
                visible = true,
                modifier = Modifier.padding(vertical = 8.dp),
                label = stringResource(R.string.ingredients_search_label),
            )
            IngredientCatalog(state, query, onOpen = { onOpenIngredient(it.id) }, onRetry = viewModel::retryCatalog)
        }
    }

    EditorSaveEffect(
        status = state.editorSave,
        editorOpen = showEditor || showScanCreate || state.scanBrandTarget != null,
        onClose = {
            showEditor = false
            showScanCreate = false
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
            commonToggle = if (state.isAdmin) CommonToggle(createCommon) { createCommon = it } else null,
        )
    }

    ScanFlow(state, viewModel, showScanCreate, editorError) { showScanCreate = it }
}

@Composable
private fun ScanButton(enabled: Boolean, onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    IconButton(enabled = enabled, onClick = { scope.launch { scanBarcode(context)?.let(onScanned) } }) {
        Icon(Icons.Rounded.QrCodeScanner, contentDescription = stringResource(R.string.ingredients_brand_scan))
    }
}

/** Eventos de una sola vez del ViewModel: navegar a una ficha o marca y reiniciar el flujo de escaneo. */
@Composable
private fun IngredientsEvents(
    state: IngredientsUiState,
    viewModel: IngredientsViewModel,
    onOpenIngredient: (String) -> Unit,
    onOpenBrand: (ingredientId: String, brandId: String) -> Unit,
    onScanEnded: () -> Unit,
) {
    // Un alta duplicada lleva a la ficha existente.
    LaunchedEffect(state.openIngredientId) {
        state.openIngredientId?.let {
            onOpenIngredient(it)
            viewModel.consumeOpenIngredient()
        }
    }
    // Al terminar o cancelar el escaneo, el siguiente empieza otra vez por la elección de ingrediente.
    LaunchedEffect(state.scanPick == null) { if (state.scanPick == null) onScanEnded() }
    // Un código ya guardado lleva a su marca en la ficha del ingrediente.
    LaunchedEffect(state.openBrand) {
        state.openBrand?.let {
            onOpenBrand(it.ingredientId, it.brandId)
            viewModel.consumeOpenBrand()
        }
    }
}

/** Escaneo desde la lista: elegir ingrediente (o crear uno personal) y revisar la marca. */
@Composable
private fun ScanFlow(
    state: IngredientsUiState,
    viewModel: IngredientsViewModel,
    showCreate: Boolean,
    editorError: String?,
    setShowCreate: (Boolean) -> Unit,
) {
    val pick = state.scanPick ?: return
    val ingredients = (state.ingredients as? UiState.Content)?.value.orEmpty()
    when {
        state.scanBrandTarget != null -> BrandEditorDialog(
            brandId = null,
            initial = BrandDraft.of(pick.brandName, pick.barcode, pick.nutrition),
            saving = state.isSaving,
            errorMessage = editorError,
            onDismiss = viewModel::cancelScan,
            onSave = viewModel::saveScannedBrand,
        )
        showCreate -> IngredientEditorDialog(
            ingredient = null,
            initialName = pick.productName,
            saving = state.isSaving,
            errorMessage = editorError,
            onDismiss = { setShowCreate(false) },
            onSave = viewModel::createPersonalForScan,
        )
        else -> ScanPickDialog(
            ingredients = ingredients,
            productName = pick.productName,
            onPick = { viewModel.pickIngredientForScan(it.id) },
            onCreatePersonal = { setShowCreate(true) },
            onDismiss = viewModel::cancelScan,
        )
    }
}

@Composable
private fun IngredientCatalog(
    state: IngredientsUiState,
    query: String,
    onOpen: (CatalogIngredient) -> Unit,
    onRetry: () -> Unit,
) {
    when (val catalog = state.ingredients) {
        UiState.Loading -> LoadingState(Modifier.fillMaxWidth())
        is UiState.Error -> ErrorState(
            Modifier.fillMaxWidth(),
            message =
            state.catalogError?.let { stringResource(it.stringResourceId) }
                ?: stringResource(catalog.failure.messageRes()),
            onRetry = onRetry,
        )
        is UiState.Content -> {
            val filtered = remember(catalog.value, query) { catalog.value.matching(query) }
            val lines = remember(state.brands, state.prefs) { ingredientRowLines(state.brands, state.prefs) }
            if (filtered.isEmpty()) {
                EmptyState(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Kitchen,
                    title = stringResource(
                        if (query.isNotBlank() && catalog.value.isNotEmpty()) {
                            R.string.ingredients_no_results
                        } else {
                            R.string.ingredients_empty
                        },
                    ),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = Spacing.fabClearance),
                ) {
                    items(filtered, key = CatalogIngredient::id) { ingredient ->
                        IngredientRow(
                            ingredient = ingredient,
                            line = lines[ingredient.id] ?: IngredientRowLine.None,
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
    commonToggle: CommonToggle? = null,
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
        if (commonToggle != null) {
            SwitchRow(
                label = stringResource(R.string.ingredients_common_switch),
                checked = commonToggle.checked,
                onCheckedChange = commonToggle.onChange,
            )
        }
    }
}

/** Estado del interruptor «Común (catálogo)» del editor de ingredientes. */
internal class CommonToggle(val checked: Boolean, val onChange: (Boolean) -> Unit)

@Composable
internal fun IngredientsFeedback(
    error: IngredientUiMessage?,
    notice: IngredientUiMessage?,
    saving: Boolean,
    onDismiss: () -> Unit,
    existingName: String? = null,
    barcodeConflict: BarcodeConflict? = null,
) {
    val message = error ?: notice
    val text = when {
        saving -> stringResource(R.string.write_saving)
        message != null -> ingredientMessageText(message, existingName, barcodeConflict)
        else -> return
    }
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
                text = text,
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
private fun ingredientMessageText(
    message: IngredientUiMessage,
    existingName: String?,
    barcodeConflict: BarcodeConflict? = null,
): String = when {
    message == IngredientUiMessage.AlreadyExists && existingName != null ->
        stringResource(R.string.ingredients_error_already_exists, existingName)
    message == IngredientUiMessage.BarcodeInOtherIngredient && barcodeConflict != null ->
        stringResource(
            R.string.ingredients_brand_barcode_elsewhere,
            barcodeConflict.brandName,
            barcodeConflict.ingredientName,
        )
    else -> stringResource(message.stringResourceId)
}
