package com.dlunaunizar.bobitos.feature.recipes

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    onBack: () -> Unit,
    canWrite: Boolean,
    modifier: Modifier = Modifier,
    viewModel: RecipesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        viewModel.observe()
        onDispose { viewModel.stopObserving() }
    }
    var detail by remember { mutableStateOf<Recipe?>(null) }
    var editorRequest by remember { mutableStateOf<RecipeEditorRequest?>(null) }
    var recipeToDelete by remember { mutableStateOf<Recipe?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipes_title)) },
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
            if (canWrite) {
                ExtendedFloatingActionButton(
                    onClick = { editorRequest = RecipeEditorRequest(recipe = null) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.recipes_create)) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            RecipesFeedback(state, viewModel::clearFeedback)
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.recipes_search_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                recipesSection(R.string.recipes_section_mine, state.mine, state.query) { detail = it }
                recipesSection(R.string.recipes_section_global, state.global, state.query) { detail = it }
            }
        }
    }

    detail?.let { recipe ->
        RecipeDetailDialog(
            recipe = recipe,
            isMine = state.owns(recipe),
            canWrite = canWrite,
            onEdit = {
                editorRequest = RecipeEditorRequest(recipe)
                detail = null
            },
            onDelete = {
                recipeToDelete = recipe
                detail = null
            },
            onFork = {
                viewModel.fork(recipe)
                detail = null
            },
            onDismiss = { detail = null },
        )
    }

    editorRequest?.let { request ->
        RecipeEditor(
            recipe = request.recipe,
            saving = state.isSaving,
            canWrite = canWrite,
            isAdmin = state.isAdmin,
            onDismiss = { editorRequest = null },
            onSave = { visibility, title, description, category ->
                val recipe = request.recipe
                if (recipe == null) {
                    viewModel.createRecipe(visibility, title, description, category)
                } else {
                    viewModel.updateRecipe(recipe.id, title, description, category)
                }
                editorRequest = null
            },
        )
    }

    recipeToDelete?.let { recipe ->
        AlertDialog(
            onDismissRequest = { recipeToDelete = null },
            title = { Text(stringResource(R.string.recipes_delete_title)) },
            text = { Text(stringResource(R.string.recipes_delete_body, recipe.title)) },
            confirmButton = {
                TextButton(
                    enabled = canWrite && !state.isSaving,
                    onClick = {
                        viewModel.deleteRecipe(recipe.id)
                        recipeToDelete = null
                    },
                ) { Text(stringResource(R.string.recipes_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { recipeToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private fun LazyListScope.recipesSection(
    @StringRes titleRes: Int,
    recipes: UiState<List<Recipe>>,
    query: String,
    onOpen: (Recipe) -> Unit,
) {
    item(key = "header-$titleRes") {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    when (recipes) {
        UiState.Loading -> item(key = "loading-$titleRes") { Text(stringResource(R.string.generic_loading)) }
        is UiState.Error -> item(key = "error-$titleRes") {
            Text(
                text = recipes.message ?: stringResource(R.string.generic_error),
                color = MaterialTheme.colorScheme.error,
            )
        }
        is UiState.Content -> {
            val filtered = recipes.value.filter { it.matches(query) }
            if (filtered.isEmpty()) {
                item(key = "empty-$titleRes") {
                    Text(
                        text = stringResource(R.string.recipes_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(filtered, key = { "$titleRes-${it.id}" }) { recipe ->
                    RecipeCard(recipe = recipe, onClick = { onOpen(recipe) })
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: Recipe, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(recipe.category, stringResource(R.string.recipes_by, recipe.createdByName))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecipeDetailDialog(
    recipe: Recipe,
    isMine: Boolean,
    canWrite: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFork: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(recipe.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(recipe.description ?: stringResource(R.string.recipes_no_description))
                recipe.category?.let { Text(stringResource(R.string.recipes_category, it)) }
                Text(
                    text = stringResource(R.string.recipes_by, recipe.createdByName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (canWrite) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isMine) {
                            TextButton(onClick = onEdit) { Text(stringResource(R.string.recipes_edit)) }
                            TextButton(onClick = onDelete) { Text(stringResource(R.string.recipes_delete)) }
                        } else {
                            TextButton(onClick = onFork) { Text(stringResource(R.string.recipes_fork)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) } },
    )
}

@Composable
private fun RecipeEditor(
    recipe: Recipe?,
    saving: Boolean,
    canWrite: Boolean,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onSave: (RecipeVisibility, String, String?, String?) -> Unit,
) {
    var title by remember(recipe?.id) { mutableStateOf(recipe?.title.orEmpty()) }
    var description by remember(recipe?.id) { mutableStateOf(recipe?.description.orEmpty()) }
    var category by remember(recipe?.id) { mutableStateOf(recipe?.category.orEmpty()) }
    var global by remember(recipe?.id) { mutableStateOf(recipe?.visibility == RecipeVisibility.GLOBAL) }
    val validation = RecipesValidation.validate(title, description, category)
    // El toggle de catálogo común solo se ofrece al crear (la visibilidad de una receta existente
    // está congelada por las reglas) y solo a quien puede publicar GLOBAL.
    val canChooseGlobal = isAdmin && recipe == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (recipe == null) R.string.recipes_add_title else R.string.recipes_edit_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.recipes_title_label)) },
                    supportingText = {
                        if (validation == RecipeUiMessage.TitleRequired || validation == RecipeUiMessage.TitleTooLong) {
                            Text(stringResource(validation.stringResourceId))
                        }
                    },
                    isError = validation == RecipeUiMessage.TitleRequired || validation == RecipeUiMessage.TitleTooLong,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.recipes_description_label)) },
                    supportingText = {
                        if (validation == RecipeUiMessage.DescriptionTooLong) {
                            Text(stringResource(validation.stringResourceId))
                        }
                    },
                    isError = validation == RecipeUiMessage.DescriptionTooLong,
                    minLines = 2,
                    maxLines = 4,
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.recipes_category_label)) },
                    supportingText = {
                        if (validation == RecipeUiMessage.CategoryTooLong) {
                            Text(stringResource(validation.stringResourceId))
                        }
                    },
                    isError = validation == RecipeUiMessage.CategoryTooLong,
                    singleLine = true,
                )
                if (canChooseGlobal) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.recipes_publish_global),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(checked = global, onCheckedChange = { global = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = validation == null && canWrite && !saving,
                onClick = {
                    val visibility = if (global) RecipeVisibility.GLOBAL else RecipeVisibility.PRIVATE
                    onSave(visibility, title, description, category)
                },
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun RecipesFeedback(state: RecipesUiState, onDismiss: () -> Unit) {
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

private data class RecipeEditorRequest(val recipe: Recipe?)

private fun RecipesUiState.owns(recipe: Recipe): Boolean =
    (mine as? UiState.Content)?.value?.any { it.id == recipe.id } == true

private fun Recipe.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val trimmed = query.trim()
    return title.contains(trimmed, ignoreCase = true) || category?.contains(trimmed, ignoreCase = true) == true
}

private val RecipeUiMessage.stringResourceId: Int
    get() = when (this) {
        RecipeUiMessage.TitleRequired -> R.string.recipes_error_title_required
        RecipeUiMessage.TitleTooLong -> R.string.recipes_error_title_too_long
        RecipeUiMessage.DescriptionTooLong -> R.string.recipes_error_description_too_long
        RecipeUiMessage.CategoryTooLong -> R.string.recipes_error_category_too_long
        RecipeUiMessage.NotAuthenticated -> R.string.space_error_not_authenticated
        RecipeUiMessage.EmailNotVerified -> R.string.space_error_email_not_verified
        RecipeUiMessage.RecipeNotFound -> R.string.recipes_error_not_found
        RecipeUiMessage.PermissionDenied -> R.string.space_error_permission_denied
        RecipeUiMessage.NetworkError -> R.string.space_error_network
        RecipeUiMessage.UnexpectedError -> R.string.space_error_unexpected
        RecipeUiMessage.RecipeSaved -> R.string.recipes_notice_saved
        RecipeUiMessage.RecipeDeleted -> R.string.recipes_notice_deleted
        RecipeUiMessage.RecipeForked -> R.string.recipes_notice_forked
    }
