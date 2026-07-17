package com.dlunaunizar.bobitos.feature.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.ShoppingItem

@Composable
fun ShoppingScreen(
    spaceId: String,
    state: ShoppingUiState,
    canWrite: Boolean,
    onObserve: (String) -> Unit,
    onStopObserving: () -> Unit,
    onAdd: (String, String?, String?) -> Unit,
    onUpdate: (String, String, String?, String?) -> Unit,
    onSetPurchased: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onClearPurchased: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(spaceId) {
        onObserve(spaceId)
        onDispose(onStopObserving)
    }

    var editedItem by remember { mutableStateOf<ShoppingItem?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<ShoppingItem?>(null) }
    var clearConfirmationVisible by remember { mutableStateOf(false) }
    val content = state.items as? UiState.Content
    val pending = content?.value?.filterNot(ShoppingItem::purchased).orEmpty()
    val purchased = content?.value?.filter(ShoppingItem::purchased).orEmpty()
    val actionsEnabled = canWrite && !state.isSaving

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                enabled = actionsEnabled,
                onClick = {
                    editedItem = null
                    editorVisible = true
                },
            ) {
                Text(stringResource(R.string.shopping_add))
            }
        }

        ShoppingFeedback(state, onClearFeedback)
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
                    ) {
                        item(key = "pending-heading") {
                            ShoppingSectionTitle(
                                title = stringResource(R.string.shopping_pending),
                                count = pending.size,
                            )
                        }
                        items(pending, key = ShoppingItem::id) { item ->
                            ShoppingItemCard(
                                item = item,
                                enabled = actionsEnabled,
                                onSetPurchased = { onSetPurchased(item.id, it) },
                                onEdit = {
                                    editedItem = item
                                    editorVisible = true
                                },
                                onDelete = { itemToDelete = item },
                            )
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
                                ShoppingItemCard(
                                    item = item,
                                    enabled = actionsEnabled,
                                    onSetPurchased = { onSetPurchased(item.id, it) },
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

    if (editorVisible) {
        ShoppingItemEditor(
            item = editedItem,
            saving = state.isSaving,
            onDismiss = { editorVisible = false },
            onSave = { name, quantity, notes ->
                val item = editedItem
                if (item == null) {
                    onAdd(name, quantity, notes)
                } else {
                    onUpdate(item.id, name, quantity, notes)
                }
                editorVisible = false
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
                        onDelete(item.id)
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
                        onClearPurchased()
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
    onSetPurchased: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = item.purchased,
                enabled = enabled,
                onCheckedChange = onSetPurchased,
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
                item.notes?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = stringResource(R.string.shopping_added_by, item.createdByName),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (item.purchased) {
                    Text(
                        text = stringResource(
                            R.string.shopping_marked_by,
                            item.purchasedByName ?: item.purchasedBy.orEmpty(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row {
                    TextButton(enabled = enabled, onClick = onEdit) {
                        Text(stringResource(R.string.shopping_edit))
                    }
                    TextButton(enabled = enabled, onClick = onDelete) {
                        Text(stringResource(R.string.shopping_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemEditor(
    item: ShoppingItem?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?) -> Unit,
) {
    var name by remember(item?.id) { mutableStateOf(item?.name.orEmpty()) }
    var quantity by remember(item?.id) { mutableStateOf(item?.quantity.orEmpty()) }
    var notes by remember(item?.id) { mutableStateOf(item?.notes.orEmpty()) }
    val validation = ShoppingValidation.validate(name, quantity, notes)

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
                        if (validation == ShoppingUiMessage.NameRequired ||
                            validation == ShoppingUiMessage.NameTooLong
                        ) {
                            Text(stringResource(validation.stringResourceId))
                        }
                    },
                    isError = validation == ShoppingUiMessage.NameRequired ||
                        validation == ShoppingUiMessage.NameTooLong,
                    singleLine = true,
                )
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = validation == null && !saving,
                onClick = { onSave(name, quantity, notes) },
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private val ShoppingUiMessage.stringResourceId: Int
    get() = when (this) {
        ShoppingUiMessage.NameRequired -> R.string.shopping_error_name_required
        ShoppingUiMessage.NameTooLong -> R.string.shopping_error_name_too_long
        ShoppingUiMessage.QuantityTooLong -> R.string.shopping_error_quantity_too_long
        ShoppingUiMessage.NotesTooLong -> R.string.shopping_error_notes_too_long
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
