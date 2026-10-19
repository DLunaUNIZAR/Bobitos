package com.dlunaunizar.bobitos.feature.pantry

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.AddFab
import com.dlunaunizar.bobitos.core.designsystem.component.CollapsibleHeaderBand
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.Feedback
import com.dlunaunizar.bobitos.core.designsystem.component.GroupedRow
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.RowPosition
import com.dlunaunizar.bobitos.core.designsystem.component.SaveFeedbackEffect
import com.dlunaunizar.bobitos.core.designsystem.component.ScreenScaffold
import com.dlunaunizar.bobitos.core.designsystem.component.SearchField
import com.dlunaunizar.bobitos.core.designsystem.component.SectionHeader
import com.dlunaunizar.bobitos.core.designsystem.component.rowPosition
import com.dlunaunizar.bobitos.core.designsystem.rememberFabExpanded
import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
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
    // Sin espacio activo no se puede escribir (las escrituras usan el espacio observado).
    val canEdit = canWrite && spaceId != null
    val items = (state.items as? UiState.Content)?.value.orEmpty()

    val listState = rememberLazyListState()
    ScreenScaffold(
        module = AppModule.PANTRY,
        title = stringResource(R.string.pantry_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            TextButton(onClick = onWhatToCook) { Text(stringResource(R.string.pantry_what_to_cook)) }
        },
        floatingActionButton = {
            if (canEdit) {
                val fabExpanded = rememberFabExpanded(listState)
                AddFab(
                    label = R.string.pantry_add,
                    expanded = fabExpanded,
                    onClick = viewModel::openPicker,
                )
            }
        },
    ) { innerPadding ->
        LocalSnackbarHostState.current?.let { host ->
            PantrySnackbars(
                state = state,
                host = host,
                onFinishedShown = viewModel::consumeFinished,
                onRestock = viewModel::restock,
            )
        }
        // Con el selector abierto, el error sale dentro del selector (el snackbar de aquí quedaría tapado).
        SaveFeedbackEffect(
            save = state.save,
            controls = viewModel.feedback,
            covered = state.picker != null,
            errorText = { stringResource(it.stringRes()) },
            noticeText = { stringResource(it.stringRes()) },
        )
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
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
                    enabled = canEdit && !state.save.isSaving,
                    onQueryChange = viewModel::setQuery,
                    onFinished = viewModel::markFinished,
                    modifier = Modifier.weight(1f),
                    listState = listState,
                )
            }
        }
    }

    state.picker?.let { picker ->
        PantryPicker(
            picker = picker,
            ingredients = state.ingredientData.ingredients,
            pantryIds = remember(items) { items.mapTo(HashSet(), PantryItem::id) },
            saving = state.save.isSaving,
            onQueryChange = viewModel::setPickerQuery,
            onToggle = viewModel::togglePick,
            onConfirm = viewModel::confirmPicker,
            feedback = state.save.error?.let { Feedback(stringResource(it.stringRes()), isError = true) },
            onFeedbackShown = viewModel.feedback::consumeError,
            onDismiss = viewModel::closePicker,
        )
    }
}

@Composable
private fun PantryContent(
    items: List<PantryItem>,
    query: String,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
    onFinished: (PantryItem) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        EmptyState(
            modifier = modifier,
            icon = Icons.Rounded.Inventory2,
            title = stringResource(R.string.pantry_empty),
            description = stringResource(R.string.pantry_empty_description),
        )
        return
    }
    val sections = remember(items, query) { pantrySections(items, query) }
    CollapsibleHeaderBand {
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            visible = true,
            modifier = Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.md),
            label = stringResource(R.string.pantry_search_label),
        )
    }
    Column(modifier) {
        if (sections.isEmpty()) {
            Text(
                stringResource(R.string.pantry_no_results),
                modifier = Modifier.padding(Spacing.lg),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            state = listState,
            // Abajo deja sitio al botón flotante para no tapar «Se acabó» de la última fila.
            contentPadding = PaddingValues(
                start = Spacing.lg,
                top = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.fabClearance,
            ),
        ) {
            sections.forEach { section ->
                item(key = pantryHeaderKey(section.category)) {
                    SectionHeader(title = section.category ?: stringResource(R.string.pantry_others))
                }
                itemsIndexed(section.items, key = { _, item -> pantryItemKey(item.id) }) { index, item ->
                    PantryRow(
                        item = item,
                        position = rowPosition(index, section.items.size),
                        enabled = enabled,
                        onFinished = { onFinished(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PantryRow(item: PantryItem, position: RowPosition, enabled: Boolean, onFinished: () -> Unit) {
    GroupedRow(position = position) {
        Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onFinished, enabled = enabled) { Text(stringResource(R.string.pantry_finished)) }
    }
}

// «Se acabó», con la acción «A la compra» durante más tiempo.
@Composable
private fun PantrySnackbars(
    state: PantryUiState,
    host: SnackbarHostState,
    onFinishedShown: () -> Unit,
    onRestock: (PantryItem) -> Unit,
) {
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
