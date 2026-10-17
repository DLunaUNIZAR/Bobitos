package com.dlunaunizar.bobitos.feature.ingredients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.formatDecimal
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosDialog
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.designsystem.component.EditorSaveEffect
import com.dlunaunizar.bobitos.core.designsystem.component.InfoChip
import com.dlunaunizar.bobitos.core.designsystem.component.ScreenScaffold
import com.dlunaunizar.bobitos.core.designsystem.component.SwitchRow
import com.dlunaunizar.bobitos.core.designsystem.component.discardFailure
import com.dlunaunizar.bobitos.core.designsystem.component.rememberEditorItem
import com.dlunaunizar.bobitos.core.designsystem.component.savingOutsideEditor
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.Nutrition
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.feature.shopping.SupermarketIcon
import com.dlunaunizar.bobitos.feature.shopping.labelRes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientDetailScreen(
    ingredientId: String,
    brandId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToShopping: ((String) -> Unit)? = null,
    viewModel: IngredientDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(ingredientId) {
        viewModel.observe(ingredientId)
        onDispose { viewModel.stopObserving() }
    }
    LaunchedEffect(state.finished) { if (state.finished) onBack() }

    var showFichaEditor by rememberSaveable { mutableStateOf(false) }
    // El editor de marca sobrevive a una rotación: se guarda el id de la marca y su borrador inicial.
    var brandEditorId by rememberSaveable { mutableStateOf<String?>(null) }
    var brandInitial by rememberSaveable { mutableStateOf<BrandDraft?>(null) }
    val openBrandEditor: (String?, BrandDraft) -> Unit = { id, initial ->
        brandEditorId = id
        brandInitial = initial
    }
    var confirmDeleteIngredient by remember { mutableStateOf(false) }
    var brandToDelete by remember { mutableStateOf<IngredientBrand?>(null) }
    val ingredient = state.ingredient
    // Se mantiene la ficha mientras el catálogo recarga (p. ej. al girar), para no perder el borrador.
    val editorIngredient = rememberEditorItem(ingredientId, ingredient, state.loaded)

    BrandEvents(brandId, state, viewModel, openBrandEditor)

    ScreenScaffold(
        title = ingredient?.name ?: stringResource(R.string.ingredients_title),
        onBack = onBack,
        modifier = modifier,
        saving = savingOutsideEditor(state.isSaving, showFichaEditor || brandInitial != null),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IngredientsMessageEffect(
                error = state.error,
                notice = state.notice,
                editorSave = state.editorSave,
                editorOpen = showFichaEditor || brandInitial != null,
                onShown = viewModel::clearFeedback,
                barcodeConflict = state.barcodeConflict,
            )
            IngredientContent(
                state = state,
                ingredient = ingredient,
                viewModel = viewModel,
                onEditFicha = { showFichaEditor = true },
                onDeleteIngredient = { confirmDeleteIngredient = true },
                onOpenBrandEditor = openBrandEditor,
                onDeleteBrand = { brandToDelete = it },
                onAddToShopping = onAddToShopping,
            )
        }
    }

    DetailEditors(
        state = state,
        viewModel = viewModel,
        fichaIngredient = editorIngredient.takeIf { showFichaEditor },
        brandId = brandEditorId,
        brandInitial = brandInitial,
        onCloseFicha = { showFichaEditor = false },
        onCloseBrand = { brandInitial = null },
    )

    DeleteConfirmations(
        ingredient = ingredient.takeIf { confirmDeleteIngredient },
        brand = brandToDelete,
        saving = state.isSaving,
        onDeleteIngredient = {
            viewModel.deleteIngredient()
            confirmDeleteIngredient = false
        },
        onDeleteBrand = {
            viewModel.deleteBrand(it.id)
            brandToDelete = null
        },
        onDismissIngredient = { confirmDeleteIngredient = false },
        onDismissBrand = { brandToDelete = null },
    )
}

@Composable
private fun DeleteConfirmations(
    ingredient: CatalogIngredient?,
    brand: IngredientBrand?,
    saving: Boolean,
    onDeleteIngredient: () -> Unit,
    onDeleteBrand: (IngredientBrand) -> Unit,
    onDismissIngredient: () -> Unit,
    onDismissBrand: () -> Unit,
) {
    if (ingredient != null) {
        BobitosDialog(
            title = stringResource(R.string.ingredients_delete_title),
            message = stringResource(deleteBodyRes(ingredient), ingredient.name),
            confirmLabel = stringResource(R.string.ingredients_delete),
            destructive = true,
            confirmEnabled = !saving,
            onConfirm = onDeleteIngredient,
            onDismiss = onDismissIngredient,
        )
    }
    if (brand != null) {
        BobitosDialog(
            title = stringResource(R.string.ingredients_brand_delete_title),
            message = stringResource(R.string.ingredients_brand_delete_body, brand.name),
            confirmLabel = stringResource(R.string.ingredients_delete),
            destructive = true,
            confirmEnabled = !saving,
            onConfirm = { onDeleteBrand(brand) },
            onDismiss = onDismissBrand,
        )
    }
}

/** Editores de la ficha y de la marca; el guardado confirmado por el servidor cierra el que esté abierto. */
@Composable
private fun DetailEditors(
    state: IngredientDetailUiState,
    viewModel: IngredientDetailViewModel,
    fichaIngredient: CatalogIngredient?,
    brandId: String?,
    brandInitial: BrandDraft?,
    onCloseFicha: () -> Unit,
    onCloseBrand: () -> Unit,
) {
    EditorSaveEffect(
        status = state.editorSave,
        editorOpen = fichaIngredient != null || brandInitial != null,
        onClose = {
            onCloseFicha()
            onCloseBrand()
        },
        onConsume = viewModel::consumeEditorSave,
    )
    val editorError = editorErrorMessage(state.error, state.editorSave)
    if (fichaIngredient != null) {
        IngredientEditorDialog(
            ingredient = fichaIngredient,
            saving = state.isSaving,
            errorMessage = editorError,
            onDismiss = {
                state.editorSave.discardFailure(viewModel::clearFeedback)
                onCloseFicha()
            },
            onSave = viewModel::updateIngredient,
        )
    }
    BrandEditorHost(
        brandId = brandId,
        initial = brandInitial,
        saving = state.isSaving,
        errorMessage = editorError,
        onClose = {
            state.editorSave.discardFailure(viewModel::clearFeedback)
            onCloseBrand()
        },
        onSave = viewModel::saveBrand,
    )
}

@Composable
private fun IngredientContent(
    state: IngredientDetailUiState,
    ingredient: CatalogIngredient?,
    viewModel: IngredientDetailViewModel,
    onEditFicha: () -> Unit,
    onDeleteIngredient: () -> Unit,
    onOpenBrandEditor: (String?, BrandDraft) -> Unit,
    onDeleteBrand: (IngredientBrand) -> Unit,
    onAddToShopping: ((String) -> Unit)?,
) {
    if (ingredient == null) {
        Text(
            stringResource(if (state.loaded) R.string.ingredients_error_not_found else R.string.write_saving),
            modifier = Modifier.padding(top = 16.dp),
        )
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    FichaSection(
        ingredient = ingredient,
        canEdit = state.canEditIngredient,
        onEdit = onEditFicha,
        onDelete = onDeleteIngredient,
    )
    if (onAddToShopping != null) {
        OutlinedButton(onClick = { onAddToShopping(ingredient.name) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ingredients_add_to_shopping))
        }
    }
    StoresSection(stores = state.stores, onToggle = viewModel::toggleStore)
    BrandsSection(
        state = state,
        onFavorite = viewModel::setFavorite,
        onAdd = { onOpenBrandEditor(null, BrandDraft.of("", "", null)) },
        onScan = { scope.launch { scanBarcode(context)?.let(viewModel::lookupBarcode) } },
        onEdit = { onOpenBrandEditor(it.id, BrandDraft.of(it)) },
        onDelete = onDeleteBrand,
    )
}

/** Abre el editor de una marca: la del argumento de navegación, la de un código escaneado o una nueva. */
@Composable
private fun BrandEvents(
    brandId: String?,
    state: IngredientDetailUiState,
    viewModel: IngredientDetailViewModel,
    openBrandEditor: (String?, BrandDraft) -> Unit,
) {
    // La marca del argumento de navegación (llegada desde un escaneo en la lista) se abre una sola vez.
    var brandArgHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(brandId, state.brands) {
        if (brandId == null || brandArgHandled) return@LaunchedEffect
        state.brands.firstOrNull { it.id == brandId }?.let {
            brandArgHandled = true
            openBrandEditor(it.id, BrandDraft.of(it))
        }
    }
    // Un escaneo de una marca de este ingrediente abre su editor.
    LaunchedEffect(state.openBrandId) {
        val id = state.openBrandId ?: return@LaunchedEffect
        state.brands.firstOrNull { it.id == id }?.let { openBrandEditor(it.id, BrandDraft.of(it)) }
        viewModel.consumeOpenBrand()
    }
    // Un escaneo nuevo con éxito abre el editor de marca prerrellenado.
    LaunchedEffect(state.scannedBrand) {
        state.scannedBrand?.let { draft ->
            openBrandEditor(null, BrandDraft.of(draft.name, draft.barcode, draft.nutrition))
            viewModel.consumeScannedBrand()
        }
    }
}

@Composable
private fun FichaSection(ingredient: CatalogIngredient, canEdit: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ingredient.category?.let {
                Text(stringResource(R.string.recipes_category, it), style = MaterialTheme.typography.bodyMedium)
            }
            ingredient.defaultUnit?.let {
                Text(stringResource(R.string.ingredients_default_unit, it), style = MaterialTheme.typography.bodyMedium)
            }
            if (canEdit) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.ingredients_edit)) }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.ingredients_delete)) }
                }
            }
        }
    }
}

@Composable
private fun StoresSection(stores: List<Supermarket>, onToggle: (Supermarket) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.ingredients_stores_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.ingredients_stores_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StoreChips(selected = stores, onToggle = onToggle)
    }
}

@Composable
private fun BrandsSection(
    state: IngredientDetailUiState,
    onFavorite: (String, Boolean) -> Unit,
    onAdd: () -> Unit,
    onScan: () -> Unit,
    onEdit: (IngredientBrand) -> Unit,
    onDelete: (IngredientBrand) -> Unit,
) {
    Text(
        text = stringResource(R.string.ingredients_brands_section),
        style = MaterialTheme.typography.titleMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onScan, enabled = !state.isLookingUp) {
            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text(stringResource(R.string.ingredients_brand_scan))
        }
        OutlinedButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text(stringResource(R.string.ingredients_brand_add))
        }
    }
    if (state.brands.isEmpty()) {
        Text(
            stringResource(R.string.ingredients_brands_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        state.brands.forEach { brand ->
            BrandCard(
                brand = brand,
                onFavorite = { onFavorite(brand.id, it) },
                onEdit = { onEdit(brand) },
                onDelete = { onDelete(brand) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrandCard(
    brand: IngredientBrand,
    onFavorite: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showNutrition by rememberSaveable(brand.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(brand.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconToggleButton(checked = brand.favorite, onCheckedChange = onFavorite) {
                    Icon(
                        imageVector = if (brand.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = stringResource(favoriteDescriptionRes(brand.favorite), brand.name),
                    )
                }
            }
            if (brand.stores.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    brand.stores.forEach { store ->
                        InfoChip(label = stringResource(store.labelRes), icon = { SupermarketIcon(store) })
                    }
                }
            }
            brand.nutrition?.let { nutrition ->
                TextButton(onClick = { showNutrition = !showNutrition }) {
                    Text(stringResource(nutritionToggleRes(showNutrition)))
                }
                if (showNutrition) NutritionSummary(nutrition)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.ingredients_edit)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.ingredients_delete)) }
            }
        }
    }
}

@Composable
private fun NutritionSummary(nutrition: Nutrition) {
    val parts = listOfNotNull(
        nutrition.energyKcal?.let { stringResource(R.string.nutrition_energy, formatDecimal(it)) },
        nutrition.fat?.let { stringResource(R.string.nutrition_fat, formatDecimal(it)) },
        nutrition.carbohydrates?.let { stringResource(R.string.nutrition_carbs, formatDecimal(it)) },
        nutrition.sugars?.let { stringResource(R.string.nutrition_sugars, formatDecimal(it)) },
        nutrition.protein?.let { stringResource(R.string.nutrition_protein, formatDecimal(it)) },
        nutrition.salt?.let { stringResource(R.string.nutrition_salt, formatDecimal(it)) },
    )
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// Muestra el editor de marca mientras haya valores iniciales guardados (nueva, editar o recién escaneada).
@Composable
private fun BrandEditorHost(
    brandId: String?,
    initial: BrandDraft?,
    saving: Boolean,
    errorMessage: String?,
    onClose: () -> Unit,
    onSave: (String?, BrandDraft) -> Unit,
) {
    if (initial == null) return
    BrandEditorDialog(
        brandId = brandId,
        initial = initial,
        saving = saving,
        errorMessage = errorMessage,
        onDismiss = onClose,
        onSave = { draft -> onSave(brandId, draft) },
    )
}

@Composable
internal fun BrandEditorDialog(
    brandId: String?,
    initial: BrandDraft,
    saving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (BrandDraft) -> Unit,
) {
    var draft by rememberSaveable(brandId, initial) { mutableStateOf(initial) }
    BobitosFormSheet(
        title = stringResource(
            if (brandId == null) R.string.ingredients_brand_add_title else R.string.ingredients_brand_edit_title,
        ),
        confirmLabel = stringResource(R.string.save),
        confirmEnabled = draft.name.isNotBlank(),
        saving = saving,
        dirty = { draft != initial },
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = { onSave(draft) },
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it) },
            label = { Text(stringResource(R.string.ingredients_brand_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.barcode,
            onValueChange = { draft = draft.copy(barcode = it) },
            label = { Text(stringResource(R.string.ingredients_brand_barcode_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.ingredients_brand_stores_label), style = MaterialTheme.typography.titleSmall)
        StoreChips(
            selected = draft.stores,
            onToggle = { draft = draft.copy(stores = toggleStore(draft.stores, it)) },
        )
        SwitchRow(
            label = stringResource(R.string.ingredients_brand_favorite_switch),
            checked = draft.favorite,
            onCheckedChange = { draft = draft.copy(favorite = it) },
        )
        Text(
            stringResource(R.string.nutrition_section),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        NUTRITION_LABELS.forEachIndexed { index, labelRes ->
            OutlinedTextField(
                value = draft.nutrition[index],
                onValueChange = { draft = draft.withNutrition(index, it) },
                label = { Text(stringResource(labelRes)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// Etiquetas de los 6 campos nutricionales, en el orden de BrandDraft.nutrition.
private val NUTRITION_LABELS = listOf(
    R.string.nutrition_energy_label,
    R.string.nutrition_fat_label,
    R.string.nutrition_carbs_label,
    R.string.nutrition_sugars_label,
    R.string.nutrition_protein_label,
    R.string.nutrition_salt_label,
)

private fun deleteBodyRes(ingredient: CatalogIngredient) =
    if (ingredient.personal) R.string.ingredients_delete_personal_body else R.string.ingredients_delete_body

private fun favoriteDescriptionRes(favorite: Boolean) =
    if (favorite) R.string.ingredients_brand_unfavorite else R.string.ingredients_brand_favorite

private fun nutritionToggleRes(shown: Boolean) =
    if (shown) R.string.ingredients_nutrition_hide else R.string.ingredients_nutrition_show
