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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosDialog
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosTopBar
import com.dlunaunizar.bobitos.core.designsystem.component.EditorSaveEffect
import com.dlunaunizar.bobitos.core.designsystem.component.EmptyState
import com.dlunaunizar.bobitos.core.designsystem.component.ErrorState
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.feature.common.IngredientReviewDialog
import com.dlunaunizar.bobitos.feature.common.PantryLine
import com.dlunaunizar.bobitos.feature.common.RecipeRow
import com.dlunaunizar.bobitos.feature.common.pantryLineText
import com.dlunaunizar.bobitos.feature.common.withPantryLines

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    onBack: () -> Unit,
    canWrite: Boolean,
    modifier: Modifier = Modifier,
    spaceId: String? = null,
    importUrl: String? = null,
    onImportUrlConsumed: () -> Unit = {},
    pantryFilterRequested: Boolean = false,
    onPantryFilterConsumed: () -> Unit = {},
    viewModel: RecipesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        viewModel.observe()
        onDispose { viewModel.stopObserving() }
    }
    PantryFilterEffects(
        spaceId = spaceId,
        requested = pantryFilterRequested,
        onObservePantry = viewModel::observePantry,
        onRequested = {
            viewModel.setPantryOnly(true)
            onPantryFilterConsumed()
        },
    )
    // Enlace compartido desde el navegador: se importa una sola vez y se consume.
    LaunchedEffect(importUrl) {
        importUrl?.let {
            viewModel.importFromUrl(it)
            onImportUrlConsumed()
        }
    }
    // Detalle y editor sobreviven a una rotación: se guarda el id de la receta y, si viene de una importación,
    // el borrador importado como lista de textos (los objetos de dominio no caben en un Bundle).
    var detailRecipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorRecipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var imported by rememberSaveable { mutableStateOf<RecipeDraft?>(null) }
    var recipeToDelete by remember { mutableStateOf<Recipe?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val pantryIds = state.pantryIds?.takeIf { state.pantryOnly }

    // Cuando la importación produce un borrador, se abre el editor prerrellenado para revisarlo.
    LaunchedEffect(state.importDraft) {
        state.importDraft?.let { draft ->
            editorRecipeId = null
            imported = RecipeDraft.of(null, draft)
            editorOpen = true
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

    // «Deshacer» solo cuando el servidor ha confirmado el borrado: si falla, sale el error y no se ofrece.
    LaunchedEffect(state.deletedRecipe) {
        val deleted = state.deletedRecipe ?: return@LaunchedEffect
        viewModel.consumeDeletedRecipe()
        scope.launchUndo(snackbarHostState, deletedMessage, undoLabel) { viewModel.restoreRecipe(deleted) }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            BobitosTopBar(
                title = stringResource(R.string.recipes_title),
                onBack = onBack,
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
                    onClick = {
                        editorRecipeId = null
                        imported = null
                        editorOpen = true
                    },
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
            RecipeFilters(
                categories = categories,
                selected = selectedCategory,
                onSelect = { selectedCategory = it },
                pantryAvailable = state.pantryIds != null,
                pantryOnly = state.pantryOnly,
                onPantryOnly = viewModel::setPantryOnly,
            )
            val filtering = state.query.isNotBlank() || selectedCategory != null || pantryIds != null
            val mineRows = rememberRecipeRows(state.mine, state.query, selectedCategory, pantryIds)
            val globalRows = rememberRecipeRows(state.global, state.query, selectedCategory, pantryIds)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                recipesSection(R.string.recipes_section_mine, state.mine, mineRows, filtering) {
                    detailRecipeId = it.id
                }
                recipesSection(R.string.recipes_section_global, state.global, globalRows, filtering) {
                    detailRecipeId = it.id
                }
            }
        }
    }

    EditorSaveEffect(
        status = state.editorSave,
        editorOpen = editorOpen,
        onClose = {
            editorOpen = false
            editorRecipeId = null
            imported = null
        },
        onConsume = viewModel::consumeEditorSave,
    )
    RecipeSheetsHost(
        state = state,
        canWrite = canWrite,
        canAddToShopping = spaceId != null,
        detailRecipeId = detailRecipeId,
        editorOpen = editorOpen,
        editorRecipeId = editorRecipeId,
        imported = imported,
        actions = remember(viewModel, spaceId) {
            RecipeSheetActions(
                onCloseDetail = { detailRecipeId = null },
                onCloseEditor = {
                    editorOpen = false
                    editorRecipeId = null
                    imported = null
                },
                onAddToShopping = { recipe ->
                    spaceId?.let { viewModel.addToShopping(it, recipe) }
                    detailRecipeId = null
                },
                onEdit = { recipe ->
                    imported = null
                    editorRecipeId = recipe.id
                    editorOpen = true
                    detailRecipeId = null
                },
                onDelete = { recipe ->
                    recipeToDelete = recipe
                    detailRecipeId = null
                },
                onFork = { recipe ->
                    viewModel.fork(recipe)
                    detailRecipeId = null
                },
                onSave = { recipe, draft -> viewModel.saveDraft(recipe?.id, draft) },
            )
        },
    )

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
        BobitosDialog(
            title = stringResource(R.string.recipes_delete_title),
            message = stringResource(R.string.recipes_delete_body, recipe.title),
            confirmLabel = stringResource(R.string.recipes_delete),
            destructive = true,
            confirmEnabled = canWrite && !state.isSaving,
            onConfirm = {
                viewModel.deleteRecipe(recipe)
                recipeToDelete = null
            },
            onDismiss = { recipeToDelete = null },
        )
    }

    state.ingredientReview?.let { reviewRows ->
        if (spaceId != null) {
            IngredientReviewDialog(
                rows = reviewRows,
                onConfirm = { choices -> viewModel.confirmShoppingReview(spaceId, choices) },
                onDismiss = viewModel::dismissShoppingReview,
            )
        }
    }
}

// Las filas de una sección, calculadas solo cuando cambian las recetas, la búsqueda, la categoría o la despensa.
@Composable
private fun rememberRecipeRows(
    recipes: UiState<List<Recipe>>,
    query: String,
    category: String?,
    pantryIds: Set<String>?,
): List<RecipeRow> = remember(recipes, query, category, pantryIds) {
    if (recipes !is UiState.Content) {
        emptyList()
    } else {
        recipes.value.matching(query)
            .filter { category == null || it.category == category }
            .withPantryLines(pantryIds)
    }
}

private fun LazyListScope.recipesSection(
    @StringRes titleRes: Int,
    recipes: UiState<List<Recipe>>,
    rows: List<RecipeRow>,
    filtering: Boolean,
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
            if (rows.isEmpty()) {
                val noResults = filtering && recipes.value.isNotEmpty()
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
                items(rows, key = { "$titleRes-${it.recipe.id}" }) { row ->
                    RecipeCard(
                        recipe = row.recipe,
                        pantryLine = row.pantryLine,
                        onClick = { onOpen(row.recipe) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

// La despensa del espacio activo para «Con lo que hay» y, desde «¿Qué cocino?», el chip activado una sola vez.
@Composable
private fun PantryFilterEffects(
    spaceId: String?,
    requested: Boolean,
    onObservePantry: (String?) -> Unit,
    onRequested: () -> Unit,
) {
    DisposableEffect(spaceId) {
        onObservePantry(spaceId)
        // Al salir no se suelta aquí: el stopObserving() de la pantalla cancela el listener y conserva los ids.
        onDispose { }
    }
    LaunchedEffect(requested) {
        if (requested) onRequested()
    }
}

// Fila de filtros: «Con lo que hay» (si se puede leer la despensa) y las categorías. Sin ninguno, no sale.
@Composable
private fun RecipeFilters(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    pantryAvailable: Boolean,
    pantryOnly: Boolean,
    onPantryOnly: (Boolean) -> Unit,
) {
    if (categories.isEmpty() && !pantryAvailable) return
    val rowState = rememberLazyListState()
    // Si el chip aparece cuando la fila ya muestra «Todas», la LazyRow se queda anclada en «Todas» y el chip
    // queda fuera de pantalla. Si la fila estaba en su inicio (índice 0 antes de insertar, 1 tras anclar, sin
    // desplazamiento), se vuelve al principio; si el usuario ya la había desplazado, no se toca.
    LaunchedEffect(pantryAvailable) {
        val atStart = rowState.firstVisibleItemIndex <= 1 && rowState.firstVisibleItemScrollOffset == 0
        if (pantryAvailable && atStart) rowState.scrollToItem(0)
    }
    LazyRow(
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        if (pantryAvailable) {
            item(key = "pantry") {
                FilterChip(
                    selected = pantryOnly,
                    onClick = { onPantryOnly(!pantryOnly) },
                    label = { Text(stringResource(R.string.recipes_filter_pantry)) },
                )
            }
        }
        if (categories.isNotEmpty()) {
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
}

@Composable
private fun RecipeCard(recipe: Recipe, pantryLine: PantryLine?, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            listOfNotNull(
                recipe.category,
                recipe.createdByName.takeIf(String::isNotBlank)?.let { stringResource(R.string.recipes_by, it) },
            ).joinToString(" · ").takeIf(String::isNotEmpty)?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            pantryLine?.let { line ->
                Text(
                    text = pantryLineText(line),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
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

private fun UiState<List<Recipe>>.contentOrEmpty(): List<Recipe> = (this as? UiState.Content)?.value.orEmpty()

private fun RecipeUiMessage.isImport(): Boolean = this in IMPORT_MESSAGES

private val IMPORT_MESSAGES = setOf(
    RecipeUiMessage.ImportInvalidUrl,
    RecipeUiMessage.ImportNetwork,
    RecipeUiMessage.ImportNotHtml,
    RecipeUiMessage.ImportNoRecipe,
    RecipeUiMessage.ImportTooLarge,
)

internal val RecipeUiMessage.stringResourceId: Int
    get() = when (this) {
        RecipeUiMessage.TitleRequired -> R.string.recipes_error_title_required
        RecipeUiMessage.TitleTooLong -> R.string.recipes_error_title_too_long
        RecipeUiMessage.DescriptionTooLong -> R.string.recipes_error_description_too_long
        RecipeUiMessage.CategoryTooLong -> R.string.recipes_error_category_too_long
        RecipeUiMessage.TooManySteps -> R.string.recipes_error_too_many_steps
        RecipeUiMessage.StepTooLong -> R.string.recipes_error_step_too_long
        RecipeUiMessage.ServingsInvalid -> R.string.recipes_error_servings
        RecipeUiMessage.TotalMinutesInvalid -> R.string.recipes_error_total_minutes
        RecipeUiMessage.NotAuthenticated -> R.string.space_error_not_authenticated
        RecipeUiMessage.EmailNotVerified -> R.string.space_error_email_not_verified
        RecipeUiMessage.RecipeNotFound -> R.string.recipes_error_not_found
        RecipeUiMessage.PermissionDenied -> R.string.space_error_permission_denied
        RecipeUiMessage.NetworkError -> R.string.space_error_network
        RecipeUiMessage.UnexpectedError -> R.string.space_error_unexpected
        RecipeUiMessage.ShoppingChanged -> R.string.recipes_error_shopping_changed
        RecipeUiMessage.SaveTimeout -> R.string.write_timeout
        RecipeUiMessage.RecipeSaved -> R.string.recipes_notice_saved
        RecipeUiMessage.RecipeDeleted -> R.string.recipes_notice_deleted
        RecipeUiMessage.RecipeForked -> R.string.recipes_notice_forked
        RecipeUiMessage.ImportInvalidUrl -> R.string.recipes_import_error_invalid_url
        RecipeUiMessage.ImportNetwork -> R.string.recipes_import_error_network
        RecipeUiMessage.ImportNotHtml -> R.string.recipes_import_error_not_html
        RecipeUiMessage.ImportNoRecipe -> R.string.recipes_import_error_no_recipe
        RecipeUiMessage.ImportTooLarge -> R.string.recipes_import_error_too_large
        RecipeUiMessage.AddedToShopping -> R.string.recipes_notice_added_to_shopping
        RecipeUiMessage.NothingToAddToShopping -> R.string.recipes_notice_nothing_to_add
        RecipeUiMessage.NoIngredients -> R.string.recipes_error_no_ingredients
    }
