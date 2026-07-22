package com.dlunaunizar.bobitos.feature.ingredients

import androidx.annotation.StringRes
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientPref
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.feature.shopping.SupermarketDropdown
import com.dlunaunizar.bobitos.feature.shopping.SupermarketIcon
import com.dlunaunizar.bobitos.feature.shopping.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IngredientsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        viewModel.observe()
        onDispose { viewModel.stopObserving() }
    }
    var editor by remember { mutableStateOf<IngredientEditorRequest?>(null) }
    var detailFor by remember { mutableStateOf<CatalogIngredient?>(null) }
    var toDelete by remember { mutableStateOf<CatalogIngredient?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { viewModel.setQuery(query) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ingredients_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editor = IngredientEditorRequest(null) },
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
            IngredientsFeedback(state, viewModel::clearFeedback)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.ingredients_search_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
            IngredientCatalog(state, onOpen = { detailFor = it })
        }
    }

    editor?.let { request ->
        IngredientEditorDialog(
            ingredient = request.ingredient,
            saving = state.isSaving,
            onDismiss = { editor = null },
            onSave = { name, category, unit ->
                val existing = request.ingredient
                if (existing == null) {
                    viewModel.createIngredient(name, category, unit)
                } else {
                    viewModel.updateIngredient(existing.id, name, category, unit)
                }
                editor = null
            },
        )
    }

    detailFor?.let { ingredient ->
        IngredientDetailDialog(
            ingredient = ingredient,
            pref = state.prefs[ingredient.id],
            canEdit = state.canEdit(ingredient),
            saving = state.isSaving,
            onSavePref = { supermarket, brand ->
                viewModel.setPref(ingredient.id, supermarket, brand)
                detailFor = null
            },
            onClearPref = {
                viewModel.clearPref(ingredient.id)
                detailFor = null
            },
            onEdit = {
                editor = IngredientEditorRequest(ingredient)
                detailFor = null
            },
            onDelete = {
                toDelete = ingredient
                detailFor = null
            },
            onDismiss = { detailFor = null },
        )
    }

    toDelete?.let { ingredient ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.ingredients_delete_title)) },
            text = { Text(stringResource(R.string.ingredients_delete_body, ingredient.name)) },
            confirmButton = {
                TextButton(
                    enabled = !state.isSaving,
                    onClick = {
                        viewModel.deleteIngredient(ingredient.id)
                        toDelete = null
                    },
                ) { Text(stringResource(R.string.ingredients_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun IngredientCatalog(state: IngredientsUiState, onOpen: (CatalogIngredient) -> Unit) {
    when (val catalog = state.catalog) {
        UiState.Loading -> LoadingState(Modifier.fillMaxWidth())
        is UiState.Error -> ErrorState(Modifier.fillMaxWidth(), message = catalog.message)
        is UiState.Content -> {
            val filtered = catalog.value.filter { it.matches(state.query) }
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
                            pref = state.prefs[ingredient.id],
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
    pref: IngredientPref?,
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
            }
            PrefSummary(pref)
        }
    }
}

@Composable
private fun PrefSummary(pref: IngredientPref?) {
    if (pref == null) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        pref.supermarket?.let { SupermarketIcon(it) }
        val label = pref.brand ?: pref.supermarket?.let { stringResource(it.labelRes) }
        label?.let {
            Text(text = it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun IngredientDetailDialog(
    ingredient: CatalogIngredient,
    pref: IngredientPref?,
    canEdit: Boolean,
    saving: Boolean,
    onSavePref: (Supermarket?, String?) -> Unit,
    onClearPref: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var supermarket by remember(ingredient.id) { mutableStateOf(pref?.supermarket) }
    var brand by remember(ingredient.id) { mutableStateOf(pref?.brand.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ingredient.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.ingredients_pref_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(R.string.ingredients_pref_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SupermarketDropdown(selected = supermarket, onSelect = { supermarket = it })
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text(stringResource(R.string.shopping_brand_label)) },
                    singleLine = true,
                )
                if (pref != null) {
                    TextButton(enabled = !saving, onClick = onClearPref) {
                        Text(stringResource(R.string.ingredients_pref_clear))
                    }
                }
                if (canEdit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onEdit) { Text(stringResource(R.string.ingredients_edit)) }
                        TextButton(onClick = onDelete) { Text(stringResource(R.string.ingredients_delete)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = { onSavePref(supermarket, brand.trim().ifBlank { null }) },
            ) { Text(stringResource(R.string.ingredients_pref_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun IngredientEditorDialog(
    ingredient: CatalogIngredient?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?) -> Unit,
) {
    var name by remember(ingredient?.id) { mutableStateOf(ingredient?.name.orEmpty()) }
    var category by remember(ingredient?.id) { mutableStateOf(ingredient?.category.orEmpty()) }
    var unit by remember(ingredient?.id) { mutableStateOf(ingredient?.defaultUnit.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (ingredient == null) R.string.ingredients_add_title else R.string.ingredients_edit_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ingredients_name_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.ingredients_category_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text(stringResource(R.string.ingredients_unit_label)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !saving,
                onClick = { onSave(name, category.trim().ifBlank { null }, unit.trim().ifBlank { null }) },
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun IngredientsFeedback(state: IngredientsUiState, onDismiss: () -> Unit) {
    val message = state.error ?: state.notice
    if (message == null && !state.isSaving) return
    val isError = state.error != null
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(modifier = Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (state.isSaving) {
                    stringResource(R.string.write_saving)
                } else {
                    stringResource(message!!.stringResourceId)
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!state.isSaving) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
            }
        }
    }
}

private data class IngredientEditorRequest(val ingredient: CatalogIngredient?)

private fun CatalogIngredient.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val trimmed = query.trim()
    return name.contains(trimmed, ignoreCase = true) || category?.contains(trimmed, ignoreCase = true) == true
}

@get:StringRes
private val IngredientUiMessage.stringResourceId: Int
    get() = when (this) {
        IngredientUiMessage.NameRequired -> R.string.ingredients_error_name_required
        IngredientUiMessage.NameTooLong -> R.string.ingredients_error_name_too_long
        IngredientUiMessage.CategoryTooLong -> R.string.ingredients_error_category_too_long
        IngredientUiMessage.UnitTooLong -> R.string.ingredients_error_unit_too_long
        IngredientUiMessage.AlreadyExists -> R.string.ingredients_error_already_exists
        IngredientUiMessage.NotFound -> R.string.ingredients_error_not_found
        IngredientUiMessage.NotAuthenticated -> R.string.space_error_not_authenticated
        IngredientUiMessage.EmailNotVerified -> R.string.space_error_email_not_verified
        IngredientUiMessage.PermissionDenied -> R.string.space_error_permission_denied
        IngredientUiMessage.NetworkError -> R.string.space_error_network
        IngredientUiMessage.UnexpectedError -> R.string.space_error_unexpected
        IngredientUiMessage.Saved -> R.string.ingredients_notice_saved
        IngredientUiMessage.Deleted -> R.string.ingredients_notice_deleted
        IngredientUiMessage.PrefSaved -> R.string.ingredients_notice_pref_saved
        IngredientUiMessage.PrefCleared -> R.string.ingredients_notice_pref_cleared
    }
