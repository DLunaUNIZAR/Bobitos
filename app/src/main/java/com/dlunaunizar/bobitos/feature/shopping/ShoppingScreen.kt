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
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.SearchField
import com.dlunaunizar.bobitos.core.designsystem.component.SwipeAction
import com.dlunaunizar.bobitos.core.designsystem.component.SwipeActionsBox
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.designsystem.theme.categoryCardColors
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.ShoppingItem
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.core.model.slug

@Composable
fun ShoppingScreen(
    spaceId: String,
    canWrite: Boolean,
    modifier: Modifier = Modifier,
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(spaceId) {
        viewModel.observe(spaceId)
        onDispose { viewModel.stopObserving() }
    }

    var editedItem by remember { mutableStateOf<ShoppingItem?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    // Catálogo + preferencias solo mientras el editor está abierto (listener acotado y perezoso).
    LaunchedEffect(editorVisible) {
        if (editorVisible) viewModel.startIngredientAssist() else viewModel.stopIngredientAssist()
    }
    var itemToDelete by remember { mutableStateOf<ShoppingItem?>(null) }
    var clearConfirmationVisible by remember { mutableStateOf(false) }
    var duplicatePrompt by remember { mutableStateOf<ShoppingDuplicate?>(null) }
    val content = state.items as? UiState.Content
    val allItems = content?.value.orEmpty()
    val findByName: (String) -> ShoppingItem? = { raw ->
        raw.trim().takeIf(String::isNotEmpty)?.let { name ->
            allItems.firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedSupermarket by remember { mutableStateOf<Supermarket?>(null) }
    val presentSupermarkets = allItems.mapNotNull(ShoppingItem::supermarket).distinct()
    val activeSupermarket = resolveSupermarket(selectedSupermarket, presentSupermarkets)
    val filteredItems = allItems.filter { it.matchesQuery(query) }.forSupermarket(activeSupermarket)
    val pending = filteredItems.filterNot(ShoppingItem::purchased)
    val purchased = filteredItems.filter(ShoppingItem::purchased)
    val actionsEnabled = canWrite && !state.isSaving
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.shopping_undo_deleted)
    val undoLabel = stringResource(R.string.undo)
    val deleteColor = MaterialTheme.colorScheme.error
    val checkColor = MaterialTheme.colorScheme.primary
    val deleteItemWithUndo: (ShoppingItem) -> Unit = { item ->
        viewModel.deleteItem(spaceId, item.id)
        scope.launchUndo(snackbar, deletedMessage, undoLabel) {
            viewModel.addItem(spaceId, item.name, item.quantity, item.notes, item.supermarket, item.brand)
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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

            ShoppingFeedback(state, viewModel::clearFeedback)
            Spacer(modifier = Modifier.height(8.dp))

            when (val itemsState = state.items) {
                UiState.Loading -> Text(stringResource(R.string.generic_loading))
                is UiState.Error -> Text(
                    text = itemsState.message ?: stringResource(R.string.generic_error),
                    color = MaterialTheme.colorScheme.error,
                )
                is UiState.Content -> {
                    if (itemsState.value.isEmpty()) {
                        ShoppingEmptyState(modifier = Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 88.dp),
                        ) {
                            item(key = "pending-heading") {
                                ShoppingSectionTitle(
                                    title = stringResource(R.string.shopping_pending),
                                    count = pending.size,
                                )
                            }
                            items(pending, key = ShoppingItem::id) { item ->
                                SwipeActionsBox(
                                    startAction = SwipeAction(Icons.Rounded.Check, checkColor) {
                                        viewModel.setPurchased(spaceId, item.id, true)
                                    }.takeIf { actionsEnabled },
                                    endAction = SwipeAction(Icons.Rounded.Delete, deleteColor) {
                                        deleteItemWithUndo(item)
                                    }.takeIf { actionsEnabled },
                                    modifier = Modifier.animateItem(),
                                ) {
                                    ShoppingItemCard(
                                        item = item,
                                        enabled = actionsEnabled,
                                        suggestion = suggestedPref(item, state.ingredientPrefs),
                                        onApplySuggestion = { viewModel.applyIngredientPref(spaceId, item) },
                                        onSetPurchased = { viewModel.setPurchased(spaceId, item.id, it) },
                                        onEdit = {
                                            editedItem = item
                                            editorVisible = true
                                        },
                                        onDelete = { itemToDelete = item },
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
                                        startAction = SwipeAction(Icons.Rounded.Check, checkColor) {
                                            viewModel.setPurchased(spaceId, item.id, false)
                                        }.takeIf { actionsEnabled },
                                        endAction = SwipeAction(Icons.Rounded.Delete, deleteColor) {
                                            deleteItemWithUndo(item)
                                        }.takeIf { actionsEnabled },
                                        modifier = Modifier.animateItem(),
                                    ) {
                                        ShoppingItemCard(
                                            item = item,
                                            enabled = actionsEnabled,
                                            suggestion = null,
                                            onApplySuggestion = {},
                                            onSetPurchased = { viewModel.setPurchased(spaceId, item.id, it) },
                                            onEdit = {
                                                editedItem = item
                                                editorVisible = true
                                            },
                                            onDelete = { itemToDelete = item },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (canWrite) {
            ExtendedFloatingActionButton(
                onClick = {
                    editedItem = null
                    editorVisible = true
                },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.shopping_add)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }

    if (editorVisible) {
        ShoppingItemEditor(
            item = editedItem,
            saving = state.isSaving,
            resolveDuplicate = findByName,
            suggestions = { queryText -> catalogSuggestions(state.catalog, queryText) },
            prefFor = { name -> state.ingredientPrefs[slug(name)] },
            onDismiss = { editorVisible = false },
            onSave = { name, quantity, notes, supermarket, brand ->
                val item = editedItem
                val existing = if (item == null) findByName(name) else null
                when {
                    item != null -> viewModel.updateItem(spaceId, item.id, name, quantity, notes, supermarket, brand)
                    existing != null ->
                        duplicatePrompt =
                            ShoppingDuplicate(existing, name, quantity, notes, supermarket, brand)
                    else -> viewModel.addItem(spaceId, name, quantity, notes, supermarket, brand)
                }
                editorVisible = false
            },
        )
    }

    duplicatePrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { duplicatePrompt = null },
            title = { Text(stringResource(R.string.shopping_duplicate_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.shopping_duplicate_body,
                        prompt.existing.name,
                        prompt.existing.quantity ?: "—",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateItem(
                        spaceId,
                        prompt.existing.id,
                        prompt.existing.name,
                        prompt.quantity,
                        prompt.existing.notes,
                        prompt.existing.supermarket,
                        prompt.existing.brand,
                    )
                    duplicatePrompt = null
                }) { Text(stringResource(R.string.shopping_duplicate_update)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.addItem(
                        spaceId,
                        prompt.name,
                        prompt.quantity,
                        prompt.notes,
                        prompt.supermarket,
                        prompt.brand,
                    )
                    duplicatePrompt = null
                }) { Text(stringResource(R.string.shopping_duplicate_add)) }
            },
        )
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.shopping_delete_title)) },
            text = { Text(stringResource(R.string.shopping_delete_body, item.name)) },
            confirmButton = {
                TextButton(
                    enabled = actionsEnabled,
                    onClick = {
                        deleteItemWithUndo(item)
                        itemToDelete = null
                    },
                ) { Text(stringResource(R.string.shopping_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (clearConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { clearConfirmationVisible = false },
            title = { Text(stringResource(R.string.shopping_clear_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.shopping_clear_body,
                        purchased.size,
                        purchased.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = actionsEnabled,
                    onClick = {
                        viewModel.clearPurchased(spaceId)
                        clearConfirmationVisible = false
                    },
                ) { Text(stringResource(R.string.shopping_clear_purchased)) }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirmationVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ShoppingFeedback(state: ShoppingUiState, onDismiss: () -> Unit) {
    val message = state.error ?: state.notice
    if (message == null && !state.isSaving) return
    val isError = state.error != null
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.isSaving) {
                    stringResource(R.string.write_saving)
                } else if (message == ShoppingUiMessage.PurchasedCleared) {
                    pluralStringResource(
                        R.plurals.shopping_notice_cleared,
                        state.lastClearedCount,
                        state.lastClearedCount,
                    )
                } else {
                    stringResource(message!!.stringResourceId)
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!state.isSaving) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
    }
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
    suggestion: IngredientPref?,
    onApplySuggestion: () -> Unit,
    onSetPurchased: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                if (suggestion != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val label = listOfNotNull(
                            suggestion.supermarket?.let { stringResource(it.labelRes) },
                            suggestion.brand,
                        ).joinToString(" · ")
                        Text(
                            text = stringResource(R.string.shopping_suggested, label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(enabled = enabled, onClick = onApplySuggestion) {
                            Text(stringResource(R.string.shopping_apply))
                        }
                    }
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
                        text = { Text(stringResource(R.string.shopping_delete)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemEditor(
    item: ShoppingItem?,
    saving: Boolean,
    resolveDuplicate: (String) -> ShoppingItem?,
    suggestions: (String) -> List<String>,
    prefFor: (String) -> IngredientPref?,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, Supermarket?, String?) -> Unit,
) {
    var name by remember(item?.id) { mutableStateOf(item?.name.orEmpty()) }
    var quantity by remember(item?.id) { mutableStateOf(item?.quantity.orEmpty()) }
    var notes by remember(item?.id) { mutableStateOf(item?.notes.orEmpty()) }
    var supermarket by remember(item?.id) { mutableStateOf(item?.supermarket) }
    var brand by remember(item?.id) { mutableStateOf(item?.brand.orEmpty()) }
    val validation = ShoppingValidation.validate(name, quantity, notes)
    // Solo al crear: avisa si ya hay un producto con ese nombre en la lista.
    val duplicate = if (item == null) resolveDuplicate(name) else null
    val nameError = validation == ShoppingUiMessage.NameRequired || validation == ShoppingUiMessage.NameTooLong

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (item == null) R.string.shopping_add_title else R.string.shopping_edit_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.shopping_name_label)) },
                    supportingText = {
                        when {
                            nameError -> Text(stringResource(validation!!.stringResourceId))
                            duplicate != null -> Text(
                                stringResource(R.string.shopping_already_in_list) +
                                    (duplicate.quantity?.let { " · $it" } ?: ""),
                            )
                        }
                    },
                    isError = nameError,
                    singleLine = true,
                )
                val nameSuggestions = if (item == null) suggestions(name) else emptyList()
                if (nameSuggestions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(nameSuggestions, key = { it }) { suggestion ->
                            AssistChip(
                                onClick = {
                                    name = suggestion
                                    prefFor(suggestion)?.let { pref ->
                                        pref.supermarket?.let { supermarket = it }
                                        pref.brand?.let { brand = it }
                                    }
                                },
                                label = { Text(suggestion) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(stringResource(R.string.shopping_quantity_label)) },
                    supportingText = {
                        if (validation == ShoppingUiMessage.QuantityTooLong) {
                            Text(stringResource(validation.stringResourceId))
                        }
                    },
                    isError = validation == ShoppingUiMessage.QuantityTooLong,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.shopping_notes_label)) },
                    supportingText = {
                        if (validation == ShoppingUiMessage.NotesTooLong) {
                            Text(stringResource(validation.stringResourceId))
                        }
                    },
                    isError = validation == ShoppingUiMessage.NotesTooLong,
                    minLines = 2,
                    maxLines = 4,
                )
                SupermarketAndBrandFields(
                    supermarket = supermarket,
                    onSupermarket = { supermarket = it },
                    brand = brand,
                    onBrand = { brand = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = validation == null && !saving,
                onClick = { onSave(name, quantity, notes, supermarket, brand.trim().ifEmpty { null }) },
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
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
    OutlinedTextField(
        value = brand,
        onValueChange = onBrand,
        label = { Text(stringResource(R.string.shopping_brand_label)) },
        singleLine = true,
    )
}

private fun resolveSupermarket(selected: Supermarket?, present: List<Supermarket>): Supermarket? =
    selected?.takeIf(present::contains)

// Producto que ya existe al intentar añadir uno con el mismo nombre + los valores tecleados.
private data class ShoppingDuplicate(
    val existing: ShoppingItem,
    val name: String,
    val quantity: String?,
    val notes: String?,
    val supermarket: Supermarket?,
    val brand: String?,
)

// Coincidencia por texto (nombre, marca o notas) para el buscador; en blanco no filtra.
private fun ShoppingItem.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val trimmed = query.trim()
    return name.contains(trimmed, ignoreCase = true) ||
        brand?.contains(trimmed, ignoreCase = true) == true ||
        notes?.contains(trimmed, ignoreCase = true) == true
}

private fun List<ShoppingItem>.forSupermarket(supermarket: Supermarket?): List<ShoppingItem> =
    if (supermarket == null) {
        this
    } else {
        // Al filtrar por un supermercado también aparecen los productos sin supermercado y los
        // marcados como "Indiferente" (valen para cualquier supermercado).
        filter {
            it.supermarket == supermarket ||
                it.supermarket == null ||
                it.supermarket == Supermarket.INDIFERENTE
        }
    }

@Composable
internal fun SupermarketDropdown(selected: Supermarket?, onSelect: (Supermarket?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
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
            Supermarket.entries.forEach { option ->
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
        imageVector = Icons.Rounded.Storefront,
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
        ShoppingUiMessage.PermissionDenied -> R.string.space_error_permission_denied
        ShoppingUiMessage.NetworkError -> R.string.space_error_network
        ShoppingUiMessage.UnexpectedError -> R.string.space_error_unexpected
        ShoppingUiMessage.ItemAdded -> R.string.shopping_notice_added
        ShoppingUiMessage.ItemUpdated -> R.string.shopping_notice_updated
        ShoppingUiMessage.ItemMarked -> R.string.shopping_notice_marked
        ShoppingUiMessage.ItemUnmarked -> R.string.shopping_notice_unmarked
        ShoppingUiMessage.ItemDeleted -> R.string.shopping_notice_deleted
        ShoppingUiMessage.PurchasedCleared -> R.string.shopping_notice_deleted
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

// Preferencia a sugerir para un ítem sin super ni marca (o null si ya tiene alguno o no hay pref).
private fun suggestedPref(item: ShoppingItem, prefs: Map<String, IngredientPref>): IngredientPref? {
    if (item.supermarket != null || !item.brand.isNullOrBlank()) return null
    return prefs[slug(item.name)]
}
