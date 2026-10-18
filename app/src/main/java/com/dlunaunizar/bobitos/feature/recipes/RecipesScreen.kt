package com.dlunaunizar.bobitos.feature.recipes

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.messageRes
import com.dlunaunizar.bobitos.core.designsystem.component.CreatedUndoEffect
import com.dlunaunizar.bobitos.core.designsystem.component.DeclareHeaderBand
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
import com.dlunaunizar.bobitos.core.designsystem.component.ModuleHeaderBand
import com.dlunaunizar.bobitos.core.designsystem.component.RowPosition
import com.dlunaunizar.bobitos.core.designsystem.component.ScreenScaffold
import com.dlunaunizar.bobitos.core.designsystem.component.SectionHeader
import com.dlunaunizar.bobitos.core.designsystem.component.discardFailure
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.designsystem.component.rowPosition
import com.dlunaunizar.bobitos.core.designsystem.component.savingOutsideEditor
import com.dlunaunizar.bobitos.core.designsystem.rememberFabExpanded
import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.Recipe
import com.dlunaunizar.bobitos.feature.common.CatalogGroup
import com.dlunaunizar.bobitos.feature.common.CatalogHeader
import com.dlunaunizar.bobitos.feature.common.CatalogLayout
import com.dlunaunizar.bobitos.feature.common.IngredientReviewDialog
import com.dlunaunizar.bobitos.feature.common.RecipeRow
import com.dlunaunizar.bobitos.feature.common.ScrollToTopOnChange
import com.dlunaunizar.bobitos.feature.common.groupCatalog
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
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.recipes_undo_deleted)
    val undoLabel = stringResource(R.string.undo)

    val closeEditor: () -> Unit = {
        // Cerrar tras un guardado fallido descarta ese error (ya se veía en la hoja).
        state.editorSave.discardFailure(viewModel::clearFeedback)
        editorOpen = false
        editorRecipeId = null
        imported = null
    }

    val listState = rememberLazyListState()
    ScreenScaffold(
        module = AppModule.MEALS,
        title = stringResource(R.string.recipes_title),
        onBack = onBack,
        modifier = modifier,
        saving = savingOutsideEditor(state.isSaving, editorOpen),
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
        floatingActionButton = {
            if (canWrite) {
                val fabExpanded = rememberFabExpanded(listState)
                ExtendedFloatingActionButton(
                    expanded = fabExpanded,
                    onClick = {
                        editorRecipeId = null
                        imported = null
                        editorOpen = true
                    },
                    icon = {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = if (fabExpanded) null else stringResource(R.string.recipes_create),
                        )
                    },
                    text = { Text(stringResource(R.string.recipes_create)) },
                )
            }
        },
    ) { padding ->
        // «Deshacer» solo cuando el servidor ha confirmado el borrado: si falla, sale el error y no se ofrece.
        val snackbarHost = LocalSnackbarHostState.current
        LaunchedEffect(state.deletedRecipe) {
            val deleted = state.deletedRecipe ?: return@LaunchedEffect
            viewModel.consumeDeletedRecipe()
            scope.launchUndo(snackbarHost, deletedMessage, undoLabel) { viewModel.restoreRecipe(deleted) }
        }
        CreatedUndoEffect(state.created, viewModel::consumeCreated) { viewModel.undoCreate(it.id) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // El error de la importación ya se ve dentro de su diálogo.
            val error = state.visibleError(importDialogOpen = showImport)
            FeedbackSnackbarEffect(
                error = error,
                notice = state.notice,
                editorSave = state.editorSave,
                editorOpen = editorOpen,
                onShown = viewModel::clearFeedback,
                text = { stringResource(it.stringResourceId) },
            )
            val filtering = state.query.isNotBlank() || selectedCategory != null || pantryIds != null
            // Búsqueda o «Con lo que hay»: lista plana (sin cabeceras); si no, agrupada por categoría.
            val layout = recipeLayout(state.query, pantryIds)
            val groups =
                rememberRecipeGroups(state.mine, state.global, state.query, selectedCategory, pantryIds, layout)
            // Los filtros quedan fuera de la lista: al activar o apagar «Con lo que hay» la lista se reordena y,
            // si estaba desplazada, se quedaría anclada en una receta que se ha movido; vuelve arriba.
            ScrollToTopOnChange(state.pantryOnly.toString(), listState)
            // La franja va dentro de la lista y sale de composición al desplazar: se declara aquí, fuera.
            DeclareHeaderBand()
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = Spacing.fabClearance),
            ) {
                // Búsqueda y filtros en la franja teñida: primer elemento, se va con la lista al desplazar.
                item(key = "filters") {
                    ModuleHeaderBand {
                        Column(
                            Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.sm, bottom = Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            OutlinedTextField(
                                value = state.query,
                                onValueChange = viewModel::setQuery,
                                label = { Text(stringResource(R.string.recipes_search_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            RecipeFilters(
                                categories = categories,
                                selected = selectedCategory,
                                onSelect = { selectedCategory = it },
                                pantryAvailable = state.pantryIds != null,
                                pantryOnly = state.pantryOnly,
                                onPantryOnly = viewModel::setPantryOnly,
                            )
                        }
                    }
                }
                recipesSections(
                    mine = state.mine,
                    global = state.global,
                    groups = groups,
                    filtering = filtering,
                    showCategory = layout == CatalogLayout.FLAT && pantryIds == null,
                    onRetry = viewModel::retry,
                ) { detailRecipeId = it.id }
            }
        }
    }

    EditorSaveEffect(
        status = state.editorSave,
        editorOpen = editorOpen,
        onClose = closeEditor,
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
                onCloseEditor = closeEditor,
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
                    viewModel.deleteRecipe(recipe)
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

// Los grupos de la lista, calculados solo cuando cambian las recetas, la búsqueda, la categoría, la despensa o el modo.
// Las de «Mis recetas» van primero; con «Con lo que hay» el orden es el de la despensa dentro de cada bloque.
@Composable
private fun rememberRecipeGroups(
    mine: UiState<List<Recipe>>,
    global: UiState<List<Recipe>>,
    query: String,
    category: String?,
    pantryIds: Set<String>?,
    layout: CatalogLayout,
): List<CatalogGroup<RecipeRow>> = remember(mine, global, query, category, pantryIds, layout) {
    val mineRecipes = mine.contentOrEmpty()
    val mineIds = mineRecipes.mapTo(HashSet(), Recipe::id)
    val rows = (mineRecipes + global.contentOrEmpty()).matching(query)
        .filter { category == null || it.category == category }
        .withPantryLines(pantryIds)
    groupCatalog(
        items = rows,
        isMine = { it.recipe.id in mineIds },
        categoryOf = { it.recipe.category },
        layout = layout,
        selectedCategory = category,
    )
}

private fun LazyListScope.recipesSections(
    mine: UiState<List<Recipe>>,
    global: UiState<List<Recipe>>,
    groups: List<CatalogGroup<RecipeRow>>,
    filtering: Boolean,
    showCategory: Boolean,
    onRetry: () -> Unit,
    onOpen: (Recipe) -> Unit,
) {
    // Una fuente que aún carga o falla se ve arriba, sin esconder las recetas de la otra.
    listOf("mine" to mine, "global" to global).forEach { (key, source) ->
        when (source) {
            UiState.Loading -> item(key = "loading-$key") {
                LoadingState(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg))
            }
            is UiState.Error -> item(key = "error-$key") {
                ErrorState(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                    message = stringResource(source.failure.messageRes()),
                    onRetry = onRetry,
                )
            }
            is UiState.Content -> Unit
        }
    }
    if (mine is UiState.Content && global is UiState.Content && groups.isEmpty()) {
        val noResults = filtering && (mine.value.isNotEmpty() || global.value.isNotEmpty())
        item(key = "empty") {
            EmptyState(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                icon = Icons.Rounded.MenuBook,
                title = stringResource(if (noResults) R.string.recipes_no_results else R.string.recipes_empty),
            )
        }
    }
    groups.forEachIndexed { groupIndex, group ->
        val header = group.header
        if (header != null) {
            stickyHeader(key = "header-$groupIndex-${header.key()}", contentType = "header") {
                SectionHeader(
                    title = header.title(),
                    count = group.items.size,
                    sticky = true,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }
        itemsIndexed(group.items, key = { _, row -> "recipe-${row.recipe.id}" }) { index, row ->
            RecipeRowItem(
                row = row,
                position = rowPosition(index, group.items.size),
                showCategory = showCategory,
                onClick = { onOpen(row.recipe) },
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = if (index == group.items.size - 1) Spacing.md else 0.dp)
                    .animateItem(),
            )
        }
    }
}

private fun recipeLayout(query: String, pantryIds: Set<String>?) =
    if (query.isNotBlank() || pantryIds != null) CatalogLayout.FLAT else CatalogLayout.BY_CATEGORY

private fun CatalogHeader.key(): String = when (this) {
    CatalogHeader.Mine -> "mine"
    is CatalogHeader.Category -> "category-$name"
    CatalogHeader.Other -> "other"
}

@Composable
private fun CatalogHeader.title(): String = when (this) {
    CatalogHeader.Mine -> stringResource(R.string.recipes_section_mine)
    is CatalogHeader.Category -> name
    CatalogHeader.Other -> stringResource(R.string.recipes_group_other)
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

// Fila de filtros: el desplegable de categoría y, al lado, «Con lo que hay» (si se puede leer la despensa).
// Sin ninguno de los dos, no sale.
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
    FilterDropdownRow {
        if (categories.isNotEmpty()) {
            FilterDropdown(
                label = stringResource(R.string.recipes_filter_category_label),
                value = selected ?: stringResource(R.string.recipes_filter_all),
                options = listOf(
                    FilterOption(stringResource(R.string.recipes_filter_all), selected == null) { onSelect(null) },
                ) + categories.map { category ->
                    FilterOption(category, selected == category) { onSelect(category) }
                },
                active = selected != null,
                modifier = Modifier.weight(1f),
            )
        }
        if (pantryAvailable) {
            FilterChip(
                selected = pantryOnly,
                onClick = { onPantryOnly(!pantryOnly) },
                label = { Text(stringResource(R.string.recipes_filter_pantry)) },
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun RecipeRowItem(
    row: RecipeRow,
    position: RowPosition,
    showCategory: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recipe = row.recipe
    GroupedRow(position = position, onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            when (val supporting = recipeSupporting(recipe, row.pantryLine, showCategory)) {
                is RecipeSupporting.Pantry -> SupportingText(pantryLineText(supporting.line), pantry = true)
                is RecipeSupporting.Category -> SupportingText(supporting.name)
                is RecipeSupporting.Meta -> SupportingText(metaText(supporting))
                RecipeSupporting.None -> Unit
            }
        }
    }
}

@Composable
private fun SupportingText(text: String, pantry: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (pantry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun metaText(meta: RecipeSupporting.Meta): String {
    val minutes = meta.minutes?.let { stringResource(R.string.recipes_minutes, it) }
    val servings = meta.servings?.let { pluralStringResource(R.plurals.recipes_servings, it, it) }
    return if (minutes != null && servings != null) {
        stringResource(R.string.recipes_meta, minutes, servings)
    } else {
        minutes ?: servings.orEmpty()
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

private fun UiState<List<Recipe>>.contentOrEmpty(): List<Recipe> = (this as? UiState.Content)?.value.orEmpty()

private fun RecipesUiState.visibleError(importDialogOpen: Boolean): RecipeUiMessage? =
    error?.takeUnless { importDialogOpen && it.isImport() }

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
        RecipeUiMessage.ImportInvalidUrl -> R.string.recipes_import_error_invalid_url
        RecipeUiMessage.ImportNetwork -> R.string.recipes_import_error_network
        RecipeUiMessage.ImportNotHtml -> R.string.recipes_import_error_not_html
        RecipeUiMessage.ImportNoRecipe -> R.string.recipes_import_error_no_recipe
        RecipeUiMessage.ImportTooLarge -> R.string.recipes_import_error_too_large
        RecipeUiMessage.AddedToShopping -> R.string.recipes_notice_added_to_shopping
        RecipeUiMessage.NothingToAddToShopping -> R.string.recipes_notice_nothing_to_add
        RecipeUiMessage.NoIngredients -> R.string.recipes_error_no_ingredients
    }
