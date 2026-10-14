package com.dlunaunizar.bobitos.feature.pantry

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
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosTopBar
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.component.SearchField
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.PantryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(
    onBack: () -> Unit,
    canWrite: Boolean,
    onWhatToCook: () -> Unit,
    modifier: Modifier = Modifier,
    spaceId: String? = null,
    viewModel: PantryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(spaceId) {
        if (spaceId != null) viewModel.observe(spaceId)
        onDispose { viewModel.stopObserving() }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    PantrySnackbars(
        state = state,
        host = snackbarHostState,
        onNoticeShown = viewModel::clearFeedback,
        onFinishedShown = viewModel::consumeFinished,
        onRestock = viewModel::restock,
    )
    // Sin espacio activo no se puede escribir (las escrituras usan el espacio observado).
    val canEdit = canWrite && spaceId != null
    val items = (state.items as? UiState.Content)?.value.orEmpty()

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            BobitosTopBar(
                title = stringResource(R.string.pantry_title),
                onBack = onBack,
                actions = {
                    TextButton(onClick = onWhatToCook) { Text(stringResource(R.string.pantry_what_to_cook)) }
                },
            )
        },
        floatingActionButton = {
            if (canEdit) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::openPicker,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.pantry_add)) },
                )
            }
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            state.error?.let { message -> PantryErrorBanner(message, viewModel::clearFeedback) }
            when (val loaded = state.items) {
                UiState.Loading -> LoadingState(Modifier.weight(1f))
                is UiState.Error -> ErrorState(
                    modifier = Modifier.weight(1f),
                    message = stringResource(R.string.pantry_error_read),
                    onRetry = spaceId?.let { id -> { viewModel.observe(id) } },
                )
                is UiState.Content -> PantryContent(
                    items = loaded.value,
                    query = state.query,
                    canEdit = canEdit,
                    enabled = canEdit && !state.isSaving,
                    onQueryChange = viewModel::setQuery,
                    onFinished = viewModel::markFinished,
                    onFill = viewModel::openPicker,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    state.picker?.let { picker ->
        PantryPicker(
            picker = picker,
            ingredients = state.ingredientData.ingredients,
            pantryIds = remember(items) { items.mapTo(HashSet(), PantryItem::id) },
            saving = state.isSaving,
            onQueryChange = viewModel::setPickerQuery,
            onToggle = viewModel::togglePick,
            onConfirm = viewModel::confirmPicker,
            error = state.error,
            onDismissError = viewModel::clearFeedback,
            onDismiss = viewModel::closePicker,
        )
    }
}

@Composable
private fun PantryContent(
    items: List<PantryItem>,
    query: String,
    canEdit: Boolean,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
    onFinished: (PantryItem) -> Unit,
    onFill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        EmptyState(
            modifier = modifier,
            icon = Icons.Rounded.Inventory2,
            title = stringResource(R.string.pantry_empty),
            description = stringResource(R.string.pantry_empty_description),
            actionLabel = if (canEdit) stringResource(R.string.pantry_fill) else null,
            onAction = if (canEdit) onFill else null,
        )
        return
    }
    val sections = remember(items, query) { pantrySections(items, query) }
    Column(modifier) {
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            visible = true,
            modifier = Modifier.padding(horizontal = Spacing.lg),
            label = stringResource(R.string.pantry_search_label),
        )
        if (sections.isEmpty()) {
            Text(
                stringResource(R.string.pantry_no_results),
                modifier = Modifier.padding(Spacing.lg),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            // Abajo deja sitio al botón flotante para no tapar «Se acabó» de la última fila.
            contentPadding = PaddingValues(start = Spacing.lg, top = Spacing.lg, end = Spacing.lg, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            sections.forEach { section ->
                item(key = pantryHeaderKey(section.category)) {
                    Text(
                        text = section.category ?: stringResource(R.string.pantry_others),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
                items(section.items, key = { pantryItemKey(it.id) }) { item ->
                    PantryRow(item = item, enabled = enabled, onFinished = { onFinished(item) })
                }
            }
        }
    }
}

@Composable
private fun PantryRow(item: PantryItem, enabled: Boolean, onFinished: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onFinished, enabled = enabled) { Text(stringResource(R.string.pantry_finished)) }
    }
}

// Avisos de éxito como Snackbar efímero; «Se acabó», con la acción «A la compra» durante más tiempo.
@Composable
private fun PantrySnackbars(
    state: PantryUiState,
    host: SnackbarHostState,
    onNoticeShown: () -> Unit,
    onFinishedShown: () -> Unit,
    onRestock: (PantryItem) -> Unit,
) {
    val noticeText = state.notice?.let { stringResource(it.stringRes()) }
    LaunchedEffect(state.notice) {
        val text = noticeText ?: return@LaunchedEffect
        host.showSnackbar(text)
        onNoticeShown()
    }
    val finished = state.finished
    val finishedText = finished?.let { stringResource(R.string.pantry_finished_snackbar, it.name) }
    val toShopping = stringResource(R.string.pantry_to_shopping)
    LaunchedEffect(finished) {
        if (finished == null || finishedText == null) return@LaunchedEffect
        val result = host.showSnackbar(finishedText, actionLabel = toShopping, duration = SnackbarDuration.Long)
        onFinishedShown()
        if (result == SnackbarResult.ActionPerformed) onRestock(finished)
    }
}

@Composable
internal fun PantryErrorBanner(message: PantryUiMessage, onDismiss: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(Modifier.padding(start = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(message.stringRes()), Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        }
    }
}

private fun PantryUiMessage.stringRes() = when (this) {
    PantryUiMessage.NameRequired -> R.string.pantry_error_name_required
    PantryUiMessage.NameTooLong -> R.string.pantry_error_name_too_long
    PantryUiMessage.NotAuthenticated -> R.string.space_error_not_authenticated
    PantryUiMessage.EmailNotVerified -> R.string.space_error_email_not_verified
    PantryUiMessage.SpaceNotFound -> R.string.space_error_not_found
    PantryUiMessage.PermissionDenied -> R.string.space_error_permission_denied
    PantryUiMessage.NetworkError -> R.string.space_error_network
    PantryUiMessage.ShoppingChanged -> R.string.pantry_error_shopping_changed
    PantryUiMessage.UnexpectedError -> R.string.space_error_unexpected
    PantryUiMessage.ItemsAdded -> R.string.pantry_notice_added
    PantryUiMessage.AddedToShopping -> R.string.pantry_notice_to_shopping
    PantryUiMessage.AlreadyInShopping -> R.string.pantry_notice_already_listed
}
