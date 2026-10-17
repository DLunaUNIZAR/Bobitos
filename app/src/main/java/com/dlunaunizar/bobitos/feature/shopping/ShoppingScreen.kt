@file:OptIn(ExperimentalMaterial3Api::class)

package com.dlunaunizar.bobitos.feature.shopping

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosDialog
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.designsystem.component.EditorSaveEffect
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.Feedback
import com.dlunaunizar.bobitos.core.designsystem.component.FeedbackSnackbarEffect
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.ModuleScaffold
import com.dlunaunizar.bobitos.core.designsystem.component.SearchField
import com.dlunaunizar.bobitos.core.designsystem.component.SwipeAction
import com.dlunaunizar.bobitos.core.designsystem.component.SwipeActionsBox
import com.dlunaunizar.bobitos.core.designsystem.component.discardFailure
import com.dlunaunizar.bobitos.core.designsystem.component.feedbackFor
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.designsystem.component.rememberEditorSlot
import com.dlunaunizar.bobitos.core.designsystem.component.savingOutsideEditor
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.designsystem.theme.categoryCardColors
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
    prefillName: String? = null,
    onPrefillConsumed: () -> Unit = {},
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
    var itemToDelete by remember { mutableStateOf<ShoppingItem?>(null) }
    var clearConfirmationVisible by remember { mutableStateOf(false) }
    val content = state.items as? UiState.Content
    val allItems = content?.value.orEmpty()
    val pendingMatch: (String) -> ShoppingItem? = { raw ->
        findShoppingMatch(allItems, raw)?.takeUnless(ShoppingItem::purchased)
    }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedSupermarket by remember { mutableStateOf<Supermarket?>(null) }
    val presentSupermarkets = allItems.presentSupermarkets()
    val activeSupermarket = resolveSupermarket(selectedSupermarket, presentSupermarkets)
    val filteredItems = allItems.filter { it.matchesQuery(query) }.forSupermarket(activeSupermarket)
    val pending = filteredItems.filterNot(ShoppingItem::purchased)
    val purchased = filteredItems.filter(ShoppingItem::purchased)
    val actionsEnabled = canWrite && !state.isSaving
    ModuleScaffold(
        modifier = modifier.fillMaxSize(),
        saving = savingOutsideEditor(state.isSaving, editorVisible),
        floatingActionButton = {
            if (canWrite) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editedItemId = null
                        initialDraft = null
                        editorVisible = true
                    },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.shopping_add)) },
                )
            }
        },
    ) { padding ->
        val snackbar = LocalSnackbarHostState.current
        val scope = rememberCoroutineScope()
        val feedback = feedbackFor(state.error, state.notice, state.editorSave, editorVisible)?.let {
            Feedback(shoppingMessageText(it.message, state.lastClearedCount), it.isError)
        }
        FeedbackSnackbarEffect(feedback, viewModel::clearFeedback)
        val deletedMessage = stringResource(R.string.shopping_undo_deleted)
        val undoLabel = stringResource(R.string.undo)
        val deleteColor = MaterialTheme.colorScheme.error
        val checkColor = MaterialTheme.colorScheme.primary
        val deleteItemWithUndo: (ShoppingItem) -> Unit = { item ->
            viewModel.deleteItem(spaceId, item.id)
            scope.launchUndo(snackbar, deletedMessage, undoLabel) {
                viewModel.restoreItem(spaceId, item)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.shopping_list_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.shopping_pending_count,
                        pending.size,
                        pending.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SearchField(
                query = query,
                onQueryChange = { query = it },
                visible = allItems.isNotEmpty(),
                modifier = Modifier.padding(top = 8.dp),
            )

            if (presentSupermarkets.isNotEmpty()) {
                SupermarketFilterRow(
                    supermarkets = presentSupermarkets,
                    selected = activeSupermarket,
                    onSelect = { selectedSupermarket = it },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (val itemsState = state.items) {
                UiState.Loading -> Text(stringResource(R.string.generic_loading))
                is UiState.Error -> ErrorState(
                    modifier = Modifier.weight(1f),
                    message = stringResource(R.string.shopping_error_list_read),
                    onRetry = { viewModel.observe(spaceId) },
                )
                is UiState.Content -> {
                    if (itemsState.value.isEmpty()) {
                        ShoppingEmptyState(modifier = Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = Spacing.fabClearance),
                        ) {
                            item(key = "pending-heading") {
                                ShoppingSectionTitle(
                                    title = stringResource(R.string.shopping_pending),
                                    count = pending.size,
                                )
                            }
                            items(pending, key = ShoppingItem::id) { item ->
                                SwipeActionsBox(
                                    startAction = SwipeAction(
                                        Icons.Rounded.Check,
                                        checkColor,
                                        stringResource(R.string.swipe_item_purchased),
                                    ) {
                                        viewModel.setPurchased(spaceId, item.id, true)
                                    }.takeIf { actionsEnabled },
                                    endAction = SwipeAction(
                                        Icons.Rounded.Delete,
                                        deleteColor,
                                        stringResource(R.string.shopping_delete),
                                    ) {
                                        deleteItemWithUndo(item)
                                    }.takeIf { actionsEnabled },
                                    modifier = Modifier.animateItem(),
                                ) { accessibilityModifier ->
                                    ShoppingItemCard(
                                        item = item,
                                        enabled = actionsEnabled,
                                        onCreateIngredient = { viewModel.createIngredientFromItem(item) },
                                        onSetPurchased = { viewModel.setPurchased(spaceId, item.id, it) },
                                        onEdit = {
                                            if (actionsEnabled) {
                                                editedItemId = item.id
                                                initialDraft = null
                                                editorVisible = true
                                            }
                                        },
                                        onDelete = { itemToDelete = item },
                                        modifier = accessibilityModifier,
                                    )
                                }
                            }
                            if (purchased.isNotEmpty()) {
                                item(key = "purchased-heading") {
                                    Column {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            ShoppingSectionTitle(
                                                title = stringResource(R.string.shopping_purchased),
                                                count = purchased.size,
                                            )
                                            TextButton(
                                                enabled = actionsEnabled,
                                                onClick = { clearConfirmationVisible = true },
                                            ) {
                                                Text(stringResource(R.string.shopping_clear_purchased))
                                            }
                                        }
                                    }
                                }
                                items(purchased, key = ShoppingItem::id) { item ->
                                    SwipeActionsBox(
                                        startAction = SwipeAction(
                                            Icons.Rounded.Check,
                                            checkColor,
                                            stringResource(R.string.swipe_item_pending),
                                        ) {
                                            viewModel.setPurchased(spaceId, item.id, false)
                                        }.takeIf { actionsEnabled },
                                        endAction = SwipeAction(
                                            Icons.Rounded.Delete,
                                            deleteColor,
                                            stringResource(R.string.shopping_delete),
                                        ) {
                                            deleteItemWithUndo(item)
                                        }.takeIf { actionsEnabled },
                                        modifier = Modifier.animateItem(),
                                    ) { accessibilityModifier ->
                                        ShoppingItemCard(
                                            item = item,
                                            enabled = actionsEnabled,
                                            onCreateIngredient = { viewModel.createIngredientFromItem(item) },
                                            onSetPurchased = { viewModel.setPurchased(spaceId, item.id, it) },
                                            onEdit = {
                                                if (actionsEnabled) {
                                                    editedItemId = item.id
                                                    initialDraft = null
                                                    editorVisible = true
                                                }
                                            },
                                            onDelete = { itemToDelete = item },
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

        itemToDelete?.let { item ->
            BobitosDialog(
                title = stringResource(R.string.shopping_delete_title),
                message = stringResource(R.string.shopping_delete_body, item.name),
                confirmLabel = stringResource(R.string.shopping_delete),
                destructive = true,
                confirmEnabled = actionsEnabled,
                onConfirm = {
                    deleteItemWithUndo(item)
                    itemToDelete = null
                },
                onDismiss = { itemToDelete = null },
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.shopping_empty),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(text = stringResource(R.string.shopping_empty_description))
    }
}

@Composable
private fun ShoppingSectionTitle(title: String, count: Int) {
    Text(
        text = "$title · $count",
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun ShoppingItemCard(
    item: ShoppingItem,
    enabled: Boolean,
    onCreateIngredient: () -> Unit,
    onSetPurchased: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        // No se usa `enabled` de la Card (la atenuaría y la anunciaría «desactivada»): quien la usa
        // pasa un `onEdit` que no hace nada si no se puede editar.
        onClick = onEdit,
        modifier = modifier.fillMaxWidth(),
        colors = item.supermarket?.let { categoryCardColors(it.brandColor()) }
            ?: CardDefaults.cardColors(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item.purchased,
                enabled = enabled,
                onCheckedChange = onSetPurchased,
                modifier = Modifier.semantics { contentDescription = item.name },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = listOfNotNull(item.name, item.quantity?.let { "· $it" })
                        .joinToString(" "),
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (item.purchased) {
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
                Text(
                    text = if (item.purchased) {
                        stringResource(
                            R.string.shopping_marked_by,
                            item.purchasedByName ?: item.purchasedBy.orEmpty(),
                        )
                    } else {
                        stringResource(R.string.shopping_added_by, item.createdByName)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ShoppingItemMenu(enabled, onEdit, onCreateIngredient, onDelete)
        }
    }
}

@Composable
private fun ShoppingItemMenu(
    enabled: Boolean,
    onEdit: () -> Unit,
    onCreateIngredient: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(enabled = enabled, onClick = { menuExpanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
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
    val nameErrorMessage = validation?.takeIf {
        it == ShoppingUiMessage.NameRequired || it == ShoppingUiMessage.NameTooLong
    }
    val nameError = nameErrorMessage != null

    BobitosFormSheet(
        title = stringResource(if (item == null) R.string.shopping_add_title else R.string.shopping_edit_title),
        confirmLabel = stringResource(R.string.save),
        confirmEnabled = validation == null,
        saving = saving,
        dirty = { draft.isDirty(initial, context) },
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = { onSave(name, quantity, notes, draft.supermarket, draft.savedBrand(context)) },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { draft = draft.copy(name = it) },
            label = { Text(stringResource(R.string.shopping_name_label)) },
            supportingText = { NameHint(nameErrorMessage, duplicate, inPantry) },
            isError = nameError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        NameSuggestionChips(if (item == null) suggestions(name) else emptyList()) { suggestion ->
            draft = draft.copy(name = suggestion)
        }
        QuantityAndNotesFields(
            quantity = quantity,
            onQuantity = { draft = draft.copy(quantity = it) },
            notes = notes,
            onNotes = { draft = draft.copy(notes = it) },
            validation = validation,
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
        modifier = Modifier.fillMaxWidth(),
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
        modifier = Modifier.fillMaxWidth(),
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!brand.isNullOrBlank()) {
            Text(
                text = if (supermarket != null) "· $brand" else brand,
                style = MaterialTheme.typography.bodySmall,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.shopping_filter_all)) },
        )
        supermarkets.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                leadingIcon = { SupermarketIcon(option) },
                label = { Text(stringResource(option.labelRes)) },
            )
        }
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
