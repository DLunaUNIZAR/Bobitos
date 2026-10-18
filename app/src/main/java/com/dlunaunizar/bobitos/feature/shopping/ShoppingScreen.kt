@file:OptIn(ExperimentalMaterial3Api::class)

package com.dlunaunizar.bobitos.feature.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.AddFab
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosDialog
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.designsystem.component.Celebration
import com.dlunaunizar.bobitos.core.designsystem.component.CollapsibleHeaderBand
import com.dlunaunizar.bobitos.core.designsystem.component.CreatedUndoEffect
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
import com.dlunaunizar.bobitos.core.designsystem.component.ModuleScaffold
import com.dlunaunizar.bobitos.core.designsystem.component.MoreOptionsButton
import com.dlunaunizar.bobitos.core.designsystem.component.RowPosition
import com.dlunaunizar.bobitos.core.designsystem.component.SearchField
import com.dlunaunizar.bobitos.core.designsystem.component.SectionHeader
import com.dlunaunizar.bobitos.core.designsystem.component.SwipeAction
import com.dlunaunizar.bobitos.core.designsystem.component.SwipeActionsBox
import com.dlunaunizar.bobitos.core.designsystem.component.checkBounce
import com.dlunaunizar.bobitos.core.designsystem.component.completingExit
import com.dlunaunizar.bobitos.core.designsystem.component.discardFailure
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.designsystem.component.rememberCelebrationState
import com.dlunaunizar.bobitos.core.designsystem.component.rememberCompletingItems
import com.dlunaunizar.bobitos.core.designsystem.component.rememberEditorSlot
import com.dlunaunizar.bobitos.core.designsystem.component.rememberFormValidationState
import com.dlunaunizar.bobitos.core.designsystem.component.rowPosition
import com.dlunaunizar.bobitos.core.designsystem.component.savingOutsideEditor
import com.dlunaunizar.bobitos.core.designsystem.rememberFabExpanded
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.designsystem.theme.categoryRowTint
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.findShoppingMatch
import com.dlunaunizar.bobitos.core.model.ingredientKey
import com.dlunaunizar.bobitos.core.model.isInPantry
import com.dlunaunizar.bobitos.feature.common.BrandStorePicker
import com.dlunaunizar.bobitos.feature.common.BrandStoreSelection
import com.dlunaunizar.bobitos.feature.common.IngredientShoppingData
import com.dlunaunizar.bobitos.feature.common.contextFor
import com.dlunaunizar.bobitos.feature.common.freeBrandTooLong

@Composable
fun ShoppingScreen(
    spaceId: String,
    canWrite: Boolean,
    modifier: Modifier = Modifier,
    memberCount: Int? = null,
    prefillName: String? = null,
    onPrefillConsumed: () -> Unit = {},
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val showAuthor = showsAuthor(memberCount)

    DisposableEffect(spaceId) {
        viewModel.observe(spaceId)
        onDispose { viewModel.stopObserving() }
    }

    // El editor sobrevive a una rotación: se guarda el id del ítem, no el objeto.
    var editedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    // Borrador inicial del alta (nombre traído desde la ficha de un ingrediente); null = alta vacía.
    var initialDraft by rememberSaveable { mutableStateOf<ShoppingDraft?>(null) }
    // Se abre una sola vez y solo cuando se puede escribir: hasta entonces queda pendiente.
    LaunchedEffect(prefillName, canWrite) {
        val action = prefillAction(prefillName, canWrite)
        if (action is PrefillAction.Open) {
            editedItemId = null
            initialDraft = ShoppingDraft.prefilled(action.name)
            editorVisible = true
            onPrefillConsumed()
        }
    }
    // Ingredientes, marcas y preferencias solo mientras el editor está abierto (listeners acotados y perezosos).
    LaunchedEffect(editorVisible) {
        if (editorVisible) {
            viewModel.startIngredientAssist()
        } else {
            viewModel.stopIngredientAssist()
            initialDraft = null
        }
    }
    // Cerrar el editor tras un guardado fallido descarta ese error (ya se veía en la hoja).
    val closeEditor: () -> Unit = {
        state.editorSave.discardFailure(viewModel::clearFeedback)
        editorVisible = false
    }
    var clearConfirmationVisible by remember { mutableStateOf(false) }
    val content = state.items as? UiState.Content
    val allItems = content?.value.orEmpty()
    val pendingMatch: (String) -> ShoppingItem? = { raw ->
        findShoppingMatch(allItems, raw)?.takeUnless(ShoppingItem::purchased)
    }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedSupermarket by remember { mutableStateOf<Supermarket?>(null) }
    val presentSupermarkets = remember(allItems) { allItems.presentSupermarkets() }
    val activeSupermarket = remember(selectedSupermarket, presentSupermarkets) {
        resolveSupermarket(selectedSupermarket, presentSupermarkets)
    }
    val filteredItems = remember(allItems, query, activeSupermarket) {
        allItems.filter { it.matchesQuery(query) }.forSupermarket(activeSupermarket)
    }
    val completing = rememberCompletingItems()
    // Lo que acaba de marcarse sigue unos instantes entre las pendientes, y no se repite entre las compradas.
    val pending = completing.retain(filteredItems.filterNot(ShoppingItem::purchased), ShoppingItem::id)
    val purchased = filteredItems.filter { it.purchased && !completing.isCompleting(it.id) }
    val actionsEnabled = canWrite && !state.isSaving
    val listState = rememberLazyListState()
    val celebration = rememberCelebrationState(
        source = allItems,
        pending = allItems.filterNot(ShoppingItem::purchased).map(ShoppingItem::id).toSet(),
    )
    // Marcar y desmarcar no esperan a la puerta de `isSaving`: cada producto escribe al suyo, y un segundo toque sobre
    // el mismo mientras se marca no hace nada. Si era el último producto, se celebra.
    val markPurchased: (ShoppingItem) -> Unit = { item ->
        completing.begin(item.id, celebration) { onFinished ->
            viewModel.setPurchased(spaceId, item.id, true, onFinished)
        }
    }
    val unmarkPurchased: (ShoppingItem) -> Unit = { item ->
        if (!completing.isCompleting(item.id)) viewModel.setPurchased(spaceId, item.id, false)
    }
    ModuleScaffold(
        modifier = modifier.fillMaxSize(),
        saving = savingOutsideEditor(state.isSaving, editorVisible),
        floatingActionButton = {
            if (canWrite) {
                val fabExpanded = rememberFabExpanded(listState)
                AddFab(
                    label = R.string.shopping_add,
                    expanded = fabExpanded,
                    onClick = {
                        editedItemId = null
                        initialDraft = null
                        editorVisible = true
                    },
                )
            }
        },
    ) { padding ->
        val snackbar = LocalSnackbarHostState.current
        val scope = rememberCoroutineScope()
        FeedbackSnackbarEffect(
            error = state.error,
            notice = state.notice,
            editorSave = state.editorSave,
            editorOpen = editorVisible,
            onShown = viewModel::clearFeedback,
        ) { shoppingMessageText(it, state.lastClearedCount) }
        CreatedUndoEffect(state.created, viewModel::consumeCreated) { viewModel.undoCreate(it.id) }
        val deletedMessage = stringResource(R.string.shopping_undo_deleted)
        val undoLabel = stringResource(R.string.undo)
        val deleteColor = MaterialTheme.colorScheme.error
        val checkColor = MaterialTheme.colorScheme.primary
        val deleteItemWithUndo: (ShoppingItem) -> Unit = { item ->
            viewModel.deleteItem(spaceId, item.id) {
                scope.launchUndo(snackbar, deletedMessage, undoLabel) {
                    viewModel.restoreItem(spaceId, item)
                }
            }
        }

        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                when (val itemsState = state.items) {
                    UiState.Loading -> LoadingState(modifier = Modifier.weight(1f))
                    is UiState.Error -> ErrorState(
                        modifier = Modifier.weight(1f),
                        message = stringResource(R.string.shopping_error_list_read),
                        onRetry = { viewModel.observe(spaceId) },
                    )
                    is UiState.Content -> {
                        if (itemsState.value.isEmpty()) {
                            ShoppingEmptyState(modifier = Modifier.weight(1f))
                        } else {
                            // Búsqueda y filtro en la franja de la cabecera: se encoge con la barra al desplazar.
                            CollapsibleHeaderBand {
                                Column(
                                    Modifier.padding(horizontal = Spacing.lg)
                                        .padding(top = Spacing.sm, bottom = Spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    SearchField(query = query, onQueryChange = { query = it }, visible = true)
                                    SupermarketFilterRow(
                                        supermarkets = presentSupermarkets,
                                        selected = activeSupermarket,
                                        onSelect = { selectedSupermarket = it },
                                    )
                                }
                            }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = Spacing.fabClearance),
                            ) {
                                item(key = "air") { Spacer(Modifier.height(Spacing.sm)) }
                                item(key = "pending-heading") {
                                    SectionHeader(
                                        title = stringResource(R.string.shopping_pending),
                                        count = pending.size,
                                        modifier = Modifier.padding(horizontal = Spacing.lg),
                                    )
                                }
                                itemsIndexed(pending, key = { _, item -> item.id }) { index, item ->
                                    SwipeActionsBox(
                                        startAction = SwipeAction(
                                            Icons.Rounded.Check,
                                            checkColor,
                                            stringResource(R.string.swipe_item_purchased),
                                        ) {
                                            markPurchased(item)
                                        }.takeIf { canWrite },
                                        endAction = SwipeAction(
                                            Icons.Rounded.Delete,
                                            deleteColor,
                                            stringResource(R.string.shopping_delete),
                                        ) {
                                            deleteItemWithUndo(item)
                                        }.takeIf { actionsEnabled },
                                        modifier = Modifier.padding(horizontal = Spacing.lg).animateItem()
                                            .completingExit(completing, item.id),
                                    ) { accessibilityModifier ->
                                        ShoppingItemRow(
                                            item = item,
                                            position = rowPosition(index, pending.size),
                                            showAuthor = showAuthor,
                                            enabled = actionsEnabled,
                                            canToggle = canWrite,
                                            completing = completing.isCompleting(item.id),
                                            onCreateIngredient = { viewModel.createIngredientFromItem(item) },
                                            onSetPurchased = {
                                                if (it) markPurchased(item) else unmarkPurchased(item)
                                            },
                                            onEdit = {
                                                if (actionsEnabled) {
                                                    editedItemId = item.id
                                                    initialDraft = null
                                                    editorVisible = true
                                                }
                                            },
                                            onDelete = { deleteItemWithUndo(item) },
                                            modifier = accessibilityModifier,
                                        )
                                    }
                                }
                                if (purchased.isNotEmpty()) {
                                    item(key = "purchased-heading") {
                                        Column(Modifier.padding(horizontal = Spacing.lg)) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            SectionHeader(
                                                title = stringResource(R.string.shopping_purchased),
                                                count = purchased.size,
                                                action = {
                                                    TextButton(
                                                        enabled = actionsEnabled,
                                                        onClick = { clearConfirmationVisible = true },
                                                    ) {
                                                        Text(stringResource(R.string.shopping_clear_purchased))
                                                    }
                                                },
                                            )
                                        }
                                    }
                                    itemsIndexed(purchased, key = { _, item -> item.id }) { index, item ->
                                        SwipeActionsBox(
                                            startAction = SwipeAction(
                                                Icons.Rounded.Check,
                                                checkColor,
                                                stringResource(R.string.swipe_item_pending),
                                            ) {
                                                unmarkPurchased(item)
                                            }.takeIf { canWrite },
                                            endAction = SwipeAction(
                                                Icons.Rounded.Delete,
                                                deleteColor,
                                                stringResource(R.string.shopping_delete),
                                            ) {
                                                deleteItemWithUndo(item)
                                            }.takeIf { actionsEnabled },
                                            modifier = Modifier.padding(horizontal = Spacing.lg).animateItem(),
                                        ) { accessibilityModifier ->
                                            ShoppingItemRow(
                                                item = item,
                                                position = rowPosition(index, purchased.size),
                                                showAuthor = showAuthor,
                                                enabled = actionsEnabled,
                                                canToggle = canWrite,
                                                completing = false,
                                                onCreateIngredient = { viewModel.createIngredientFromItem(item) },
                                                onSetPurchased = {
                                                    if (it) markPurchased(item) else unmarkPurchased(item)
                                                },
                                                onEdit = {
                                                    if (actionsEnabled) {
                                                        editedItemId = item.id
                                                        initialDraft = null
                                                        editorVisible = true
                                                    }
                                                },
                                                onDelete = { deleteItemWithUndo(item) },
                                                modifier = accessibilityModifier,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Celebration(
                visible = celebration.visible,
                message = stringResource(R.string.celebration_shopping),
                onFinished = celebration::dismiss,
            )
        }
    }

    EditorSaveEffect(
        status = state.editorSave,
        editorOpen = editorVisible,
        onClose = { editorVisible = false },
        onConsume = viewModel::consumeEditorSave,
    )
    rememberEditorSlot(
        open = editorVisible,
        id = editedItemId,
        items = content?.value,
        idOf = ShoppingItem::id,
        onGone = closeEditor,
    )?.let { slot ->
        val editedItem = slot.item
        ShoppingItemEditor(
            item = editedItem,
            initialDraft = initialDraft,
            saving = state.isSaving,
            errorMessage = state.error?.takeIf { state.editorSave == EditorSaveStatus.FAILED }
                ?.let { stringResource(it.stringResourceId) },
            resolveDuplicate = pendingMatch,
            pantryIds = state.pantryIds,
            ingredientData = state.ingredientData,
            suggestions = { queryText -> catalogSuggestions(state.ingredientData.ingredients, queryText) },
            onDismiss = closeEditor,
            onSave = { name, quantity, notes, supermarket, brand ->
                val item = editedItem
                if (item != null) {
                    viewModel.updateItem(spaceId, item.id, name, quantity, notes, supermarket, brand)
                } else {
                    viewModel.addItem(spaceId, name, quantity, notes, supermarket, brand)
                }
            },
        )
    }

    state.duplicate?.let { duplicate ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            title = { Text(stringResource(R.string.shopping_duplicate_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val listed = duplicate.existingQuantityLabel
                    Text(
                        if (listed != null) {
                            stringResource(R.string.shopping_duplicate_body, duplicate.existing.name, listed)
                        } else {
                            stringResource(R.string.shopping_duplicate_body_plain, duplicate.existing.name)
                        },
                    )
                    if (duplicate.sumTooLong) {
                        Text(
                            text = stringResource(R.string.shopping_duplicate_sum_too_long),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.sumDuplicate(spaceId) }, enabled = !duplicate.sumTooLong) {
                    Text(stringResource(R.string.shopping_duplicate_sum))
                }
            },
            dismissButton = {
                // «No añadir» cierra también el editor; tocar fuera del aviso vuelve al editor.
                TextButton(onClick = {
                    viewModel.dismissDuplicate()
                    editorVisible = false
                }) { Text(stringResource(R.string.shopping_duplicate_skip)) }
            },
        )
    }

    if (clearConfirmationVisible) {
        BobitosDialog(
            title = stringResource(R.string.shopping_clear_title),
            message = pluralStringResource(R.plurals.shopping_clear_body, purchased.size, purchased.size),
            confirmLabel = stringResource(R.string.shopping_clear_purchased),
            destructive = true,
            confirmEnabled = actionsEnabled,
            onConfirm = {
                viewModel.clearPurchased(spaceId)
                clearConfirmationVisible = false
            },
            onDismiss = { clearConfirmationVisible = false },
        )
    }
}

@Composable
private fun shoppingMessageText(message: ShoppingUiMessage, clearedCount: Int): String =
    if (message == ShoppingUiMessage.PurchasedCleared) {
        pluralStringResource(R.plurals.shopping_notice_cleared, clearedCount, clearedCount)
    } else {
        stringResource(message.stringResourceId)
    }

@Composable
private fun ShoppingEmptyState(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.shopping_empty),
        modifier = modifier,
        icon = Icons.Rounded.ShoppingCart,
        description = stringResource(R.string.shopping_empty_description),
    )
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingItem,
    position: RowPosition,
    showAuthor: Boolean,
    enabled: Boolean,
    canToggle: Boolean,
    completing: Boolean,
    onCreateIngredient: () -> Unit,
    onSetPurchased: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupedRow(
        position = position,
        modifier = modifier,
        tint = item.supermarket?.let { categoryRowTint(it.brandColor()) },
        // No se usa `enabled` de la fila (la atenuaría y la anunciaría «desactivada»): quien la usa
        // pasa un `onEdit` que no hace nada si no se puede editar.
        onClick = onEdit,
    ) {
        val purchased = item.purchased || completing
        Checkbox(
            checked = purchased,
            // Marcar no espera a otros guardados: solo depende de poder escribir.
            enabled = canToggle,
            onCheckedChange = onSetPurchased,
            modifier = Modifier.checkBounce(completing).semantics { contentDescription = item.name },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listOfNotNull(item.name, item.quantity?.let { "· $it" })
                    .joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (purchased) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.supermarket != null || !item.brand.isNullOrBlank()) {
                SupermarketBrandLine(supermarket = item.supermarket, brand = item.brand)
            }
            item.notes?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showAuthor) {
                Text(
                    text = if (item.purchased) {
                        stringResource(
                            R.string.shopping_marked_by,
                            item.purchasedByName ?: item.purchasedBy.orEmpty(),
                        )
                    } else {
                        stringResource(R.string.shopping_added_by, item.createdByName)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ShoppingItemMenu(item.name, enabled, onEdit, onCreateIngredient, onDelete)
    }
}

@Composable
private fun ShoppingItemMenu(
    itemName: String,
    enabled: Boolean,
    onEdit: () -> Unit,
    onCreateIngredient: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        MoreOptionsButton(itemName = itemName, onClick = { menuExpanded = true }, enabled = enabled)
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.shopping_edit)) },
                onClick = {
                    menuExpanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.shopping_create_ingredient)) },
                onClick = {
                    menuExpanded = false
                    onCreateIngredient()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.shopping_delete)) },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

// Bajo el nombre: el error; si no hay, «Ya en la lista» (con su cantidad) y «Hay en la despensa».
@Composable
private fun NameHint(error: ShoppingUiMessage?, duplicate: ShoppingItem?, inPantry: Boolean) {
    if (error != null) {
        Text(stringResource(error.stringResourceId))
        return
    }
    val parts = listOfNotNull(
        duplicate?.let { listed ->
            stringResource(R.string.shopping_already_in_list) + (listed.quantity?.let { " · $it" } ?: "")
        },
        if (inPantry) stringResource(R.string.shopping_in_pantry) else null,
    )
    if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
}

@Composable
private fun ShoppingItemEditor(
    item: ShoppingItem?,
    initialDraft: ShoppingDraft?,
    saving: Boolean,
    errorMessage: String?,
    resolveDuplicate: (String) -> ShoppingItem?,
    pantryIds: Set<String>,
    ingredientData: IngredientShoppingData,
    suggestions: (String) -> List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, Supermarket?, String?) -> Unit,
) {
    val initial = initialDraft?.takeIf { item == null } ?: ShoppingDraft.of(item)
    var draft by rememberSaveable(item?.id, initialDraft) { mutableStateOf(initial) }
    val name = draft.name
    val quantity = draft.quantity
    val notes = draft.notes
    val brand = draft.brand
    // Solo al crear: un ingrediente reconocido propone marca y tienda (§5).
    val context = remember(ingredientKey(name), ingredientData) {
        if (item == null) ingredientData.contextFor(name) else null
    }
    LaunchedEffect(context) { draft = draft.withContext(context) }
    val validation = ShoppingValidation.validate(name, quantity, notes, draft.savedBrand(context))
    // Solo al crear: avisa si ya hay un producto con ese nombre en la lista y si hay en la despensa (no bloquea nada).
    val duplicate = if (item == null) resolveDuplicate(name) else null
    val inPantry = item == null && isInPantry(name, pantryIds)
    val form = rememberFormValidationState()
    val nameFocus = remember { FocusRequester() }
    val quantityFocus = remember { FocusRequester() }
    val notesFocus = remember { FocusRequester() }
    val nameErrorMessage = validation?.takeIf {
        it == ShoppingUiMessage.NameRequired || it == ShoppingUiMessage.NameTooLong
    }
    // Ningún error se ve hasta el primer intento de guardar; el del nombre es solo el de vacío o demasiado largo.
    val nameShownError = form.visible(nameErrorMessage)
    val shownValidation = form.visible(validation)

    BobitosFormSheet(
        title = stringResource(if (item == null) R.string.shopping_add_title else R.string.shopping_edit_title),
        confirmLabel = stringResource(R.string.save),
        saving = saving,
        dirty = { draft.isDirty(initial, context) },
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = {
            form.submit(
                isValid = validation == null,
                onValid = { onSave(name, quantity, notes, draft.supermarket, draft.savedBrand(context)) },
                onInvalid = { firstInvalidFocus(validation, nameFocus, quantityFocus, notesFocus).requestFocus() },
            )
        },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { draft = draft.copy(name = it) },
            label = { Text(stringResource(R.string.shopping_name_label)) },
            supportingText = { NameHint(nameShownError, duplicate, inPantry) },
            isError = nameShownError != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
        )
        NameSuggestionChips(if (item == null) suggestions(name) else emptyList()) { suggestion ->
            draft = draft.copy(name = suggestion)
        }
        QuantityAndNotesFields(
            quantity = quantity,
            onQuantity = { draft = draft.copy(quantity = it) },
            notes = notes,
            onNotes = { draft = draft.copy(notes = it) },
            validation = shownValidation,
            quantityFocus = quantityFocus,
            notesFocus = notesFocus,
        )
        if (context != null) {
            BrandStorePicker(
                context = context,
                selection = BrandStoreSelection(draft.brandId, draft.supermarket),
                freeBrand = brand,
                onBrandSelected = { draft = draft.withBrandChoice(context, it) },
                onStoreSelected = { draft = draft.copy(supermarket = it) },
                onFreeBrandChange = { draft = draft.copy(brand = it) },
            )
        } else {
            SupermarketAndBrandFields(
                supermarket = draft.supermarket,
                onSupermarket = { draft = draft.copy(supermarket = it) },
                brand = brand,
                onBrand = { draft = draft.copy(brand = it) },
            )
        }
    }
}

private fun firstInvalidFocus(
    validation: ShoppingUiMessage?,
    name: FocusRequester,
    quantity: FocusRequester,
    notes: FocusRequester,
) = when (validation) {
    ShoppingUiMessage.QuantityTooLong -> quantity
    ShoppingUiMessage.NotesTooLong -> notes
    else -> name
}

@Composable
private fun NameSuggestionChips(suggestions: List<String>, onPick: (String) -> Unit) {
    if (suggestions.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(suggestions, key = { it }) { suggestion ->
            AssistChip(onClick = { onPick(suggestion) }, label = { Text(suggestion) })
        }
    }
}

@Composable
private fun QuantityAndNotesFields(
    quantity: String,
    onQuantity: (String) -> Unit,
    notes: String,
    onNotes: (String) -> Unit,
    validation: ShoppingUiMessage?,
    quantityFocus: FocusRequester,
    notesFocus: FocusRequester,
) {
    OutlinedTextField(
        value = quantity,
        onValueChange = onQuantity,
        label = { Text(stringResource(R.string.shopping_quantity_label)) },
        supportingText = {
            if (validation == ShoppingUiMessage.QuantityTooLong) {
                Text(stringResource(validation.stringResourceId))
            }
        },
        isError = validation == ShoppingUiMessage.QuantityTooLong,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().focusRequester(quantityFocus),
    )
    OutlinedTextField(
        value = notes,
        onValueChange = onNotes,
        label = { Text(stringResource(R.string.shopping_notes_label)) },
        supportingText = {
            if (validation == ShoppingUiMessage.NotesTooLong) {
                Text(stringResource(validation.stringResourceId))
            }
        },
        isError = validation == ShoppingUiMessage.NotesTooLong,
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth().focusRequester(notesFocus),
    )
}

@Composable
private fun SupermarketAndBrandFields(
    supermarket: Supermarket?,
    onSupermarket: (Supermarket?) -> Unit,
    brand: String,
    onBrand: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.shopping_supermarket_label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SupermarketDropdown(selected = supermarket, onSelect = onSupermarket)
    val tooLong = freeBrandTooLong(brand)
    OutlinedTextField(
        value = brand,
        onValueChange = onBrand,
        label = { Text(stringResource(R.string.shopping_brand_label)) },
        singleLine = true,
        isError = tooLong,
        supportingText = if (tooLong) {
            { Text(stringResource(R.string.shopping_error_brand_too_long)) }
        } else {
            null
        },
    )
}

private fun resolveSupermarket(selected: Supermarket?, present: List<Supermarket>): Supermarket? =
    selected?.takeIf(present::contains)

// Coincidencia por texto (nombre, marca o notas) para el buscador; en blanco no filtra.
private fun ShoppingItem.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val trimmed = query.trim()
    return name.contains(trimmed, ignoreCase = true) ||
        brand?.contains(trimmed, ignoreCase = true) == true ||
        notes?.contains(trimmed, ignoreCase = true) == true
}

@Composable
internal fun SupermarketDropdown(
    selected: Supermarket?,
    onSelect: (Supermarket?) -> Unit,
    modifier: Modifier = Modifier,
    options: List<Supermarket> = Supermarket.entries,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            if (selected != null) {
                SupermarketIcon(selected, Modifier.padding(end = 8.dp))
            }
            Text(
                text = selected?.let { stringResource(it.labelRes) }
                    ?: stringResource(R.string.shopping_supermarket_none),
                modifier = Modifier.weight(1f),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.shopping_supermarket_none)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    leadingIcon = { SupermarketIcon(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun SupermarketIcon(supermarket: Supermarket, modifier: Modifier = Modifier) {
    Icon(
        imageVector = supermarket.icon(),
        contentDescription = null,
        tint = supermarket.brandColor(),
        modifier = modifier.size(16.dp),
    )
}

@Composable
private fun SupermarketBrandLine(supermarket: Supermarket?, brand: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (supermarket != null) {
            SupermarketIcon(supermarket)
            Text(
                text = stringResource(supermarket.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!brand.isNullOrBlank()) {
            Text(
                text = if (supermarket != null) "· $brand" else brand,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SupermarketFilterRow(
    supermarkets: List<Supermarket>,
    selected: Supermarket?,
    onSelect: (Supermarket?) -> Unit,
) {
    if (supermarkets.isEmpty()) return
    FilterDropdownRow {
        FilterDropdown(
            label = stringResource(R.string.shopping_filter_label),
            value = selected?.let { stringResource(it.labelRes) } ?: stringResource(R.string.shopping_filter_all),
            options = listOf(
                FilterOption(stringResource(R.string.shopping_filter_all), selected == null) { onSelect(null) },
            ) + supermarkets.map { option ->
                FilterOption(stringResource(option.labelRes), selected == option) { onSelect(option) }
            },
            active = selected != null,
            modifier = Modifier.weight(1f),
        )
    }
}

private val ShoppingUiMessage.stringResourceId: Int
    get() = when (this) {
        ShoppingUiMessage.NameRequired -> R.string.shopping_error_name_required
        ShoppingUiMessage.NameTooLong -> R.string.shopping_error_name_too_long
        ShoppingUiMessage.QuantityTooLong -> R.string.shopping_error_quantity_too_long
        ShoppingUiMessage.NotesTooLong -> R.string.shopping_error_notes_too_long
        ShoppingUiMessage.BrandTooLong -> R.string.shopping_error_brand_too_long
        ShoppingUiMessage.NotAuthenticated -> R.string.space_error_not_authenticated
        ShoppingUiMessage.EmailNotVerified -> R.string.space_error_email_not_verified
        ShoppingUiMessage.SpaceNotFound -> R.string.space_error_not_found
        ShoppingUiMessage.ItemNotFound -> R.string.shopping_error_item_not_found
        ShoppingUiMessage.ItemChanged -> R.string.shopping_error_item_changed
        ShoppingUiMessage.ListNotLoaded -> R.string.shopping_error_list_not_loaded
        ShoppingUiMessage.ListUnavailable -> R.string.shopping_error_list_unavailable
        ShoppingUiMessage.PermissionDenied -> R.string.space_error_permission_denied
        ShoppingUiMessage.NetworkError -> R.string.space_error_network
        ShoppingUiMessage.UnexpectedError -> R.string.space_error_unexpected
        ShoppingUiMessage.SaveTimeout -> R.string.write_timeout
        ShoppingUiMessage.ItemAdded -> R.string.shopping_notice_added
        ShoppingUiMessage.ItemUpdated -> R.string.shopping_notice_updated
        ShoppingUiMessage.ItemMarked -> R.string.shopping_notice_marked
        ShoppingUiMessage.ItemUnmarked -> R.string.shopping_notice_unmarked
        ShoppingUiMessage.ItemDeleted -> R.string.shopping_notice_deleted
        ShoppingUiMessage.PurchasedCleared -> R.string.shopping_notice_deleted
        ShoppingUiMessage.IngredientCreated -> R.string.shopping_notice_ingredient_created
        ShoppingUiMessage.IngredientExists -> R.string.shopping_notice_ingredient_exists
    }

// Nombres del catálogo que casan con lo tecleado (para autocompletar el ítem). Máx. 6, desde 2 letras.
private fun catalogSuggestions(catalog: List<CatalogIngredient>, query: String): List<String> {
    val trimmed = query.trim()
    if (trimmed.length < 2) return emptyList()
    return catalog.map(CatalogIngredient::name)
        .filter { it.contains(trimmed, ignoreCase = true) && !it.equals(trimmed, ignoreCase = true) }
        .distinct()
        .take(6)
}
