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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
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
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.model.Ingredient
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.core.model.RecipeVisibility
import com.dlunaunizar.bobitos.data.recipeimport.ImportedRecipe
import com.dlunaunizar.bobitos.feature.common.IngredientReviewDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    onBack: () -> Unit,
    canWrite: Boolean,
    modifier: Modifier = Modifier,
    spaceId: String? = null,
    importUrl: String? = null,
    onImportUrlConsumed: () -> Unit = {},
    viewModel: RecipesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        viewModel.observe()
        onDispose { viewModel.stopObserving() }
    }
    // Enlace compartido desde el navegador: se importa una sola vez y se consume.
    LaunchedEffect(importUrl) {
        importUrl?.let {
            viewModel.importFromUrl(it)
            onImportUrlConsumed()
        }
    }
    var detail by remember { mutableStateOf<Recipe?>(null) }
    var editorRequest by remember { mutableStateOf<RecipeEditorRequest?>(null) }
    var recipeToDelete by remember { mutableStateOf<Recipe?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }

    // Cuando la importación produce un borrador, se abre el editor prerrellenado para revisarlo.
    LaunchedEffect(state.importDraft) {
        state.importDraft?.let { draft ->
            editorRequest = RecipeEditorRequest(recipe = null, draft = draft)
            showImport = false
            viewModel.consumeImportDraft()
        }
    }
    val categories = (state.mine.contentOrEmpty() + state.global.contentOrEmpty())
        .mapNotNull { it.category?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
        .sorted()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.recipes_undo_deleted)
    val undoLabel = stringResource(R.string.undo)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                actions = {
                    if (canWrite) {
                        IconButton(onClick = { showImport = true }) {
                            Icon(
                                Icons.Rounded.Link,
                                contentDescription = stringResource(R.string.recipes_import_title),
                            )
                        }
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
            if (categories.isNotEmpty()) {
                CategoryFilter(
                    categories = categories,
                    selected = selectedCategory,
                    onSelect = { selectedCategory = it },
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                recipesSection(R.string.recipes_section_mine, state.mine, state.query, selectedCategory) { detail = it }
                recipesSection(R.string.recipes_section_global, state.global, state.query, selectedCategory) {
                    detail = it
                }
            }
        }
    }

    detail?.let { recipe ->
        RecipeDetailDialog(
            recipe = recipe,
            isMine = state.owns(recipe),
            canWrite = canWrite,
            canAddToShopping = spaceId != null,
            onAddToShopping = {
                spaceId?.let { viewModel.addToShopping(it, recipe) }
                detail = null
            },
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
            draft = request.draft,
            saving = state.isSaving,
            canWrite = canWrite,
            isAdmin = state.isAdmin,
            onDismiss = { editorRequest = null },
            onSave = { visibility, title, description, category, ingredients ->
                val recipe = request.recipe
                if (recipe == null) {
                    viewModel.createRecipe(
                        visibility,
                        title,
                        description,
                        category,
                        ingredients,
                        request.draft?.sourceUrl,
                    )
                } else {
                    viewModel.updateRecipe(recipe.id, title, description, category, ingredients)
                }
                editorRequest = null
            },
        )
    }

    if (showImport) {
        ImportUrlDialog(
            importing = state.isImporting,
            errorRes = state.error?.takeIf(RecipeUiMessage::isImport)?.stringResourceId,
            onImport = viewModel::importFromUrl,
            onDismiss = {
                showImport = false
                viewModel.clearFeedback()
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
                        scope.launchUndo(snackbarHostState, deletedMessage, undoLabel) {
                            viewModel.createRecipe(
                                recipe.visibility,
                                recipe.title,
                                recipe.description,
                                recipe.category,
                                recipe.ingredients.orEmpty(),
                            )
                        }
                    },
                ) { Text(stringResource(R.string.recipes_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { recipeToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    state.ingredientReview?.let { reviewRows ->
        if (spaceId != null) {
            IngredientReviewDialog(
                rows = reviewRows,
                onConfirm = { quantities -> viewModel.confirmShoppingReview(spaceId, quantities) },
                onDismiss = viewModel::dismissShoppingReview,
            )
        }
    }
}

private fun LazyListScope.recipesSection(
    @StringRes titleRes: Int,
    recipes: UiState<List<Recipe>>,
    query: String,
    category: String?,
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
        UiState.Loading -> item(key = "loading-$titleRes") { LoadingState(Modifier.fillMaxWidth()) }
        is UiState.Error -> item(key = "error-$titleRes") {
            ErrorState(Modifier.fillMaxWidth(), message = recipes.message)
        }
        is UiState.Content -> {
            val filtered = recipes.value.filter { it.matches(query) && (category == null || it.category == category) }
            if (filtered.isEmpty()) {
                val noResults = (query.isNotBlank() || category != null) && recipes.value.isNotEmpty()
                item(key = "empty-$titleRes") {
                    EmptyState(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.MenuBook,
                        title = stringResource(
                            if (noResults) R.string.recipes_no_results else R.string.recipes_empty,
                        ),
                    )
                }
            } else {
                items(filtered, key = { "$titleRes-${it.id}" }) { recipe ->
                    RecipeCard(recipe = recipe, onClick = { onOpen(recipe) }, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun CategoryFilter(categories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        item(key = "cat-all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.recipes_filter_all)) },
            )
        }
        items(categories, key = { it }) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category) },
            )
        }
    }
}

@Composable
private fun RecipeCard(recipe: Recipe, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
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
    canAddToShopping: Boolean,
    onAddToShopping: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFork: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(recipe.title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(recipe.description ?: stringResource(R.string.recipes_no_description))
                recipe.category?.let { Text(stringResource(R.string.recipes_category, it)) }
                IngredientsList(recipe.ingredients)
                Text(
                    text = stringResource(R.string.recipes_by, recipe.createdByName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (canWrite && canAddToShopping) {
                    TextButton(onClick = onAddToShopping) {
                        Text(stringResource(R.string.recipes_add_to_shopping))
                    }
                }
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
private fun ImportUrlDialog(
    importing: Boolean,
    @StringRes errorRes: Int?,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        title = { Text(stringResource(R.string.recipes_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.recipes_import_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.recipes_import_url_label)) },
                    singleLine = true,
                    enabled = !importing,
                    isError = errorRes != null,
                    supportingText = { errorRes?.let { Text(stringResource(it)) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (importing) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.recipes_import_loading))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = url.isNotBlank() && !importing, onClick = { onImport(url.trim()) }) {
                Text(stringResource(R.string.recipes_import_action))
            }
        },
        dismissButton = {
            TextButton(enabled = !importing, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun RecipeEditor(
    recipe: Recipe?,
    draft: ImportedRecipe?,
    saving: Boolean,
    canWrite: Boolean,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onSave: (RecipeVisibility, String, String?, String?, List<Ingredient>) -> Unit,
) {
    // Al crear a mano `recipe` y `draft` son null; al importar, los valores iniciales vienen del borrador.
    val initial = remember(recipe?.id, draft) { recipeFormInitial(recipe, draft) }
    var title by remember(initial) { mutableStateOf(initial.title) }
    var description by remember(initial) { mutableStateOf(initial.description) }
    var category by remember(initial) { mutableStateOf(initial.category) }
    var global by remember(initial) { mutableStateOf(initial.global) }
    val ingredients = remember(initial) { initial.ingredients.toMutableStateList() }
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.recipes_title_label)) },
                    supportingText = {
                        if (validation.isTitleError()) Text(stringResource(validation!!.stringResourceId))
                    },
                    isError = validation.isTitleError(),
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
                IngredientsEditor(ingredients)
            }
        },
        confirmButton = {
            TextButton(
                enabled = validation == null && canWrite && !saving,
                onClick = {
                    val visibility = if (global) RecipeVisibility.GLOBAL else RecipeVisibility.PRIVATE
                    onSave(visibility, title, description, category, ingredients.toIngredients())
                },
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun IngredientsList(ingredients: List<Ingredient>?) {
    if (ingredients.isNullOrEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.recipes_ingredients_section),
            style = MaterialTheme.typography.titleSmall,
        )
        ingredients.forEach { ingredient ->
            Text(
                text = stringResource(R.string.recipes_ingredient_bullet, ingredient.formatted()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun IngredientsEditor(rows: SnapshotStateList<IngredientDraft>) {
    Text(
        text = stringResource(R.string.recipes_ingredients_section),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    rows.forEachIndexed { index, row ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { row.name = it },
                    label = { Text(stringResource(R.string.recipes_ingredient_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { rows.removeAt(index) }) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.recipes_ingredient_remove),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = row.quantity,
                    onValueChange = { row.quantity = it },
                    label = { Text(stringResource(R.string.recipes_ingredient_quantity)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.unit,
                    onValueChange = { row.unit = it },
                    label = { Text(stringResource(R.string.recipes_ingredient_unit)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (rows.size < MAX_INGREDIENT_ROWS) {
        TextButton(onClick = { rows.add(IngredientDraft()) }) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(stringResource(R.string.recipes_ingredient_add))
        }
    }
}

private fun RecipeUiMessage?.isTitleError(): Boolean =
    this == RecipeUiMessage.TitleRequired || this == RecipeUiMessage.TitleTooLong

// Valores iniciales del editor: de la receta existente o, al importar, del borrador de la web.
private data class RecipeFormInitial(
    val title: String,
    val description: String,
    val category: String,
    val global: Boolean,
    val ingredients: List<IngredientDraft>,
)

private fun recipeFormInitial(recipe: Recipe?, draft: ImportedRecipe?): RecipeFormInitial = RecipeFormInitial(
    title = recipe?.title ?: draft?.title.orEmpty(),
    description = recipe?.description ?: draft?.description.orEmpty(),
    category = recipe?.category ?: draft?.category.orEmpty(),
    global = recipe?.visibility == RecipeVisibility.GLOBAL,
    ingredients = (recipe?.ingredients ?: draft?.ingredients).orEmpty()
        .map { IngredientDraft(it.name, it.quantity.orEmpty(), it.unit.orEmpty()) },
)

// Fila editable de ingrediente: estado mutable observable por Compose mientras dura el editor.
private class IngredientDraft(name: String = "", quantity: String = "", unit: String = "") {
    var name by mutableStateOf(name)
    var quantity by mutableStateOf(quantity)
    var unit by mutableStateOf(unit)
}

// «300 g Arroz» o «Sal» (omite cantidad/unidad ausentes).
private fun Ingredient.formatted(): String = listOfNotNull(quantity, unit, name).joinToString(" ")

// Descarta filas sin nombre y normaliza cantidad/unidad vacías a null.
private fun List<IngredientDraft>.toIngredients(): List<Ingredient> = mapNotNull { draft ->
    draft.name.trim().takeIf(String::isNotEmpty)?.let { name ->
        Ingredient(name, draft.quantity.trim().ifBlank { null }, draft.unit.trim().ifBlank { null })
    }
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

private const val MAX_INGREDIENT_ROWS = 50

private data class RecipeEditorRequest(val recipe: Recipe?, val draft: ImportedRecipe? = null)

private fun RecipesUiState.owns(recipe: Recipe): Boolean =
    (mine as? UiState.Content)?.value?.any { it.id == recipe.id } == true

private fun UiState<List<Recipe>>.contentOrEmpty(): List<Recipe> = (this as? UiState.Content)?.value.orEmpty()

private fun Recipe.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val trimmed = query.trim()
    return title.contains(trimmed, ignoreCase = true) ||
        category?.contains(trimmed, ignoreCase = true) == true ||
        ingredients?.any { it.name.contains(trimmed, ignoreCase = true) } == true
}

private fun RecipeUiMessage.isImport(): Boolean = this in IMPORT_MESSAGES

private val IMPORT_MESSAGES = setOf(
    RecipeUiMessage.ImportInvalidUrl,
    RecipeUiMessage.ImportNetwork,
    RecipeUiMessage.ImportNotHtml,
    RecipeUiMessage.ImportNoRecipe,
    RecipeUiMessage.ImportTooLarge,
)

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
        RecipeUiMessage.ImportInvalidUrl -> R.string.recipes_import_error_invalid_url
        RecipeUiMessage.ImportNetwork -> R.string.recipes_import_error_network
        RecipeUiMessage.ImportNotHtml -> R.string.recipes_import_error_not_html
        RecipeUiMessage.ImportNoRecipe -> R.string.recipes_import_error_no_recipe
        RecipeUiMessage.ImportTooLarge -> R.string.recipes_import_error_too_large
        RecipeUiMessage.AddedToShopping -> R.string.recipes_notice_added_to_shopping
        RecipeUiMessage.NoIngredients -> R.string.recipes_error_no_ingredients
    }
