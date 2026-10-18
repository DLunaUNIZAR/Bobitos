package com.dlunaunizar.bobitos.feature.ingredients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.dlunaunizar.bobitos.core.designsystem.component.AddFab
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.designsystem.component.CreatedUndoEffect
import com.dlunaunizar.bobitos.core.designsystem.component.DeclareHeaderBand
import com.dlunaunizar.bobitos.core.designsystem.component.EditorSaveEffect
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.FeedbackSnackbarEffect
import com.dlunaunizar.bobitos.core.designsystem.component.FilterDropdown
import com.dlunaunizar.bobitos.core.designsystem.component.FilterDropdownRow
import com.dlunaunizar.bobitos.core.designsystem.component.FilterOption
import com.dlunaunizar.bobitos.core.designsystem.component.GroupedRow
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.ModuleHeaderBand
import com.dlunaunizar.bobitos.core.designsystem.component.RowPosition
import com.dlunaunizar.bobitos.core.designsystem.component.ScreenScaffold
import com.dlunaunizar.bobitos.core.designsystem.component.SearchField
import com.dlunaunizar.bobitos.core.designsystem.component.SectionHeader
import com.dlunaunizar.bobitos.core.designsystem.component.SwitchRow
import com.dlunaunizar.bobitos.core.designsystem.component.discardFailure
import com.dlunaunizar.bobitos.core.designsystem.component.errorSupportingText
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.designsystem.component.rememberFormValidationState
import com.dlunaunizar.bobitos.core.designsystem.component.rowPosition
import com.dlunaunizar.bobitos.core.designsystem.component.savingOutsideEditor
import com.dlunaunizar.bobitos.core.designsystem.rememberFabExpanded
import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.feature.common.CatalogGroup
import com.dlunaunizar.bobitos.feature.common.CatalogHeader
import com.dlunaunizar.bobitos.feature.common.CatalogLayout
import com.dlunaunizar.bobitos.feature.common.groupCatalog
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
    val editorOpen = showEditor || showScanCreate || state.scanBrandTarget != null
    val closeEditor: () -> Unit = {
        // Cerrar tras un guardado fallido descarta ese error (ya se veía en el diálogo).
        state.editorSave.discardFailure(viewModel::clearFeedback)
        showEditor = false
        showScanCreate = false
    }

    val listState = rememberLazyListState()
    ScreenScaffold(
        module = AppModule.MEALS,
        title = stringResource(R.string.ingredients_title),
        onBack = onBack,
        modifier = modifier,
        saving = savingOutsideEditor(state.isSaving, editorOpen),
        actions = { ScanButton(enabled = !state.isLookingUp, onScanned = viewModel::lookupBarcode) },
        floatingActionButton = {
            val fabExpanded = rememberFabExpanded(listState)
            AddFab(
                label = R.string.ingredients_add,
                expanded = fabExpanded,
                onClick = { showEditor = true },
            )
        },
    ) { padding ->
        CreatedUndoEffect(state.created, viewModel::consumeCreated) { viewModel.undoCreate(it.id) }
        DeletedUndoEffect(viewModel)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            IngredientsMessageEffect(
                error = state.error,
                notice = state.notice,
                editorSave = state.editorSave,
                editorOpen = editorOpen,
                onShown = viewModel::clearFeedback,
                existingName = state.existingName,
            )
            IngredientCatalog(
                state = state,
                query = query,
                onQueryChange = { query = it },
                onOpen = { onOpenIngredient(it.id) },
                onRetry = viewModel::retryCatalog,
                listState = listState,
            )
        }
    }

    EditorSaveEffect(
        status = state.editorSave,
        editorOpen = editorOpen,
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
            onDismiss = closeEditor,
            onSave = { name, category, unit -> viewModel.createIngredient(name, category, unit, createCommon) },
            commonToggle = if (state.isAdmin) CommonToggle(createCommon) { createCommon = it } else null,
        )
    }

    ScanFlow(state, viewModel, showScanCreate, editorError, closeEditor) { showScanCreate = it }
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
    closeCreate: () -> Unit,
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
            onDismiss = closeCreate,
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
    onQueryChange: (String) -> Unit,
    onOpen: (CatalogIngredient) -> Unit,
    onRetry: () -> Unit,
    listState: LazyListState,
) {
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val content = state.ingredients as? UiState.Content
    val all = content?.value.orEmpty()
    // Búsqueda y filtro en la franja teñida: en la lista es su primer elemento y se va con ella al desplazar.
    val filterBand: @Composable () -> Unit = {
        IngredientFilterBand(query, onQueryChange, all, category) { category = it }
    }
    when (val catalog = state.ingredients) {
        UiState.Loading -> {
            filterBand()
            LoadingState(Modifier.fillMaxWidth())
        }
        is UiState.Error -> {
            filterBand()
            ErrorState(
                Modifier.fillMaxWidth(),
                message =
                state.catalogError?.let { stringResource(it.stringResourceId) }
                    ?: stringResource(catalog.failure.messageRes()),
                onRetry = onRetry,
            )
        }
        is UiState.Content -> {
            val filtered =
                remember(catalog.value, query, category) { catalog.value.matching(query).inCategory(category) }
            val lines = remember(state.brands, state.prefs) { ingredientRowLines(state.brands, state.prefs) }
            // Al buscar, lista plana con los tuyos primero y la categoría en la línea secundaria.
            val layout = if (query.isNotBlank()) CatalogLayout.FLAT else CatalogLayout.BY_CATEGORY
            val groups = remember(filtered, layout, category) {
                groupCatalog(
                    items = filtered,
                    isMine = CatalogIngredient::personal,
                    categoryOf = CatalogIngredient::category,
                    layout = layout,
                    selectedCategory = category,
                )
            }
            // La franja va dentro de la lista y sale de composición al desplazar: se declara aquí, fuera.
            DeclareHeaderBand()
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = Spacing.fabClearance)) {
                item(key = "filters") { filterBand() }
                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        val filtering = query.isNotBlank() || category != null
                        EmptyState(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                            icon = Icons.Rounded.Kitchen,
                            title = stringResource(
                                if (filtering && catalog.value.isNotEmpty()) {
                                    R.string.ingredients_no_results
                                } else {
                                    R.string.ingredients_empty
                                },
                            ),
                        )
                    }
                }
                ingredientGroups(groups, lines, layout == CatalogLayout.FLAT, onOpen)
            }
        }
    }
}

private fun LazyListScope.ingredientGroups(
    groups: List<CatalogGroup<CatalogIngredient>>,
    lines: Map<String, IngredientRowLine>,
    showCategory: Boolean,
    onOpen: (CatalogIngredient) -> Unit,
) {
    groups.forEachIndexed { groupIndex, group ->
        val header = group.header
        if (header != null) {
            stickyHeader(key = "header-$groupIndex", contentType = "header") {
                SectionHeader(
                    title = header.title(),
                    count = group.items.size,
                    sticky = true,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }
        itemsIndexed(group.items, key = { _, ingredient -> ingredient.id }) { index, ingredient ->
            IngredientRow(
                ingredient = ingredient,
                line = lines[ingredient.id] ?: IngredientRowLine.None,
                position = rowPosition(index, group.items.size),
                showCategory = showCategory,
                onClick = { onOpen(ingredient) },
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = if (index == group.items.size - 1) Spacing.md else 0.dp)
                    .animateItem(),
            )
        }
    }
}

@Composable
private fun CatalogHeader.title(): String = when (this) {
    CatalogHeader.Mine -> stringResource(R.string.ingredients_group_mine)
    is CatalogHeader.Category -> name
    CatalogHeader.Other -> stringResource(R.string.ingredients_group_other)
}

@Composable
private fun IngredientFilterBand(
    query: String,
    onQueryChange: (String) -> Unit,
    ingredients: List<CatalogIngredient>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val categories = remember(ingredients) { ingredients.categories() }
    ModuleHeaderBand {
        Column(
            Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                visible = true,
                label = stringResource(R.string.ingredients_search_label),
            )
            if (categories.isNotEmpty()) {
                FilterDropdownRow {
                    FilterDropdown(
                        label = stringResource(R.string.ingredients_filter_category_label),
                        value = selected ?: stringResource(R.string.ingredients_filter_all),
                        options = listOf(
                            FilterOption(stringResource(R.string.ingredients_filter_all), selected == null) {
                                onSelect(null)
                            },
                        ) + categories.map { category ->
                            FilterOption(category, selected.equals(category, ignoreCase = true)) { onSelect(category) }
                        },
                        active = selected != null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun IngredientRow(
    ingredient: CatalogIngredient,
    line: IngredientRowLine,
    position: RowPosition,
    showCategory: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupedRow(position = position, onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ingredient.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showCategory) {
                ingredient.category?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IngredientLine(line)
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
                style = MaterialTheme.typography.bodyMedium,
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
    val form = rememberFormValidationState()
    val nameFocus = remember { FocusRequester() }
    val nameBlank = draft.name.isBlank()
    val nameError = form.visible(stringResource(R.string.ingredients_error_name_required).takeIf { nameBlank })
    BobitosFormSheet(
        title = stringResource(
            if (ingredient == null) R.string.ingredients_add_title else R.string.ingredients_edit_title,
        ),
        confirmLabel = stringResource(R.string.save),
        saving = saving,
        dirty = { draft != initial },
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = {
            form.submit(
                isValid = !nameBlank,
                onValid = {
                    onSave(draft.name, draft.category.trim().ifBlank { null }, draft.unit.trim().ifBlank { null })
                },
                onInvalid = { nameFocus.requestFocus() },
            )
        },
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it) },
            label = { Text(stringResource(R.string.ingredients_name_label)) },
            singleLine = true,
            isError = nameError != null,
            supportingText = errorSupportingText(nameError),
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
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

/** Resultado de una acción del catálogo, en el snackbar de la pantalla. */
@Composable
internal fun IngredientsMessageEffect(
    error: IngredientUiMessage?,
    notice: IngredientUiMessage?,
    editorSave: EditorSaveStatus,
    editorOpen: Boolean,
    onShown: () -> Unit,
    existingName: String? = null,
    barcodeConflict: BarcodeConflict? = null,
) {
    FeedbackSnackbarEffect(error, notice, editorSave, editorOpen, onShown) {
        ingredientMessageText(it, existingName, barcodeConflict)
    }
}

/** El error del guardado del editor abierto (solo con FAILED); los demás se ven en el snackbar. */
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

/** Avisa «Ingrediente borrado» con «Deshacer» cuando el detalle publica un personal recién borrado. */
@Composable
private fun DeletedUndoEffect(viewModel: IngredientsViewModel) {
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val message = stringResource(R.string.ingredients_notice_deleted)
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(deleted) {
        val pending = deleted ?: return@LaunchedEffect
        viewModel.consumeDeleted()
        scope.launchUndo(snackbar, message, undoLabel) { viewModel.restoreIngredient(pending) }
    }
}
