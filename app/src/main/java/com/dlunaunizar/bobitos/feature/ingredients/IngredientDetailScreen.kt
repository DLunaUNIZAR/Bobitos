package com.dlunaunizar.bobitos.feature.ingredients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosTopBar
import com.dlunaunizar.bobitos.core.designsystem.component.rememberEditorItem
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.Nutrition
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.feature.shopping.SupermarketDropdown
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientDetailScreen(
    ingredientId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Un escaneo con éxito abre el editor de marca prerrellenado.
    LaunchedEffect(state.scannedBrand) {
        state.scannedBrand?.let { draft ->
            openBrandEditor(null, BrandDraft.of(draft.name, draft.barcode, draft.nutrition))
            viewModel.consumeScannedBrand()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            BobitosTopBar(
                title = ingredient?.name ?: stringResource(R.string.ingredients_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IngredientsFeedback(state.error, state.notice, state.isSaving, viewModel::clearFeedback)
            if (ingredient == null) {
                Text(
                    stringResource(if (state.loaded) R.string.ingredients_error_not_found else R.string.write_saving),
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                FichaSection(
                    ingredient = ingredient,
                    canEdit = state.canEditIngredient,
                    onEdit = { showFichaEditor = true },
                    onDelete = { confirmDeleteIngredient = true },
                )
                PreferenceSection(
                    pref = state.pref,
                    // Únicos y sin vacíos: son la clave de los chips (nombres repetidos harían crashear la LazyRow).
                    brandNames = state.brands.map(IngredientBrand::name).filter(String::isNotBlank).distinct(),
                    saving = state.isSaving,
                    onSave = viewModel::setPref,
                    onClear = viewModel::clearPref,
                )
                BrandsSection(
                    state = state,
                    onAdd = { openBrandEditor(null, BrandDraft.of("", "", null)) },
                    onScan = { scope.launch { scanBarcode(context)?.let(viewModel::lookupBarcode) } },
                    onEdit = { openBrandEditor(it.id, BrandDraft.of(it.name, it.barcode.orEmpty(), it.nutrition)) },
                    onDelete = { brandToDelete = it },
                )
            }
        }
    }

    if (showFichaEditor && editorIngredient != null) {
        IngredientEditorDialog(
            ingredient = editorIngredient,
            saving = state.isSaving,
            onDismiss = { showFichaEditor = false },
            onSave = { name, category, unit ->
                viewModel.updateIngredient(name, category, unit)
                showFichaEditor = false
            },
        )
    }

    BrandEditorHost(
        brandId = brandEditorId,
        initial = brandInitial,
        saving = state.isSaving,
        onClose = { brandInitial = null },
        onSave = { brandId, name, barcode, nutrition ->
            brandId?.let { viewModel.updateBrand(it, name, barcode, nutrition) }
                ?: viewModel.addBrand(name, barcode, nutrition)
        },
    )

    if (confirmDeleteIngredient && ingredient != null) {
        BobitosDialog(
            title = stringResource(R.string.ingredients_delete_title),
            message = stringResource(R.string.ingredients_delete_body, ingredient.name),
            confirmLabel = stringResource(R.string.ingredients_delete),
            destructive = true,
            confirmEnabled = !state.isSaving,
            onConfirm = {
                viewModel.deleteIngredient()
                confirmDeleteIngredient = false
            },
            onDismiss = { confirmDeleteIngredient = false },
        )
    }

    brandToDelete?.let { brand ->
        BobitosDialog(
            title = stringResource(R.string.ingredients_brand_delete_title),
            message = stringResource(R.string.ingredients_brand_delete_body, brand.name),
            confirmLabel = stringResource(R.string.ingredients_delete),
            destructive = true,
            confirmEnabled = !state.isSaving,
            onConfirm = {
                viewModel.deleteBrand(brand.id)
                brandToDelete = null
            },
            onDismiss = { brandToDelete = null },
        )
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
private fun PreferenceSection(
    pref: com.dlunaunizar.bobitos.core.model.IngredientPref?,
    brandNames: List<String>,
    saving: Boolean,
    onSave: (Supermarket?, String?) -> Unit,
    onClear: () -> Unit,
) {
    var supermarket by remember(pref) { mutableStateOf(pref?.supermarket) }
    var brand by remember(pref) { mutableStateOf(pref?.brand.orEmpty()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                modifier = Modifier.fillMaxWidth(),
            )
            if (brandNames.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(brandNames, key = { it }) { name ->
                        FilterChip(selected = brand == name, onClick = { brand = name }, label = { Text(name) })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !saving,
                    onClick = { onSave(supermarket, brand.trim().ifBlank { null }) },
                ) { Text(stringResource(R.string.ingredients_pref_save)) }
                if (pref != null) {
                    TextButton(enabled = !saving, onClick = onClear) {
                        Text(stringResource(R.string.ingredients_pref_clear))
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandsSection(
    state: IngredientDetailUiState,
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
                canEdit = state.canEditBrand(brand),
                onEdit = { onEdit(brand) },
                onDelete = { onDelete(brand) },
            )
        }
    }
}

@Composable
private fun BrandCard(brand: IngredientBrand, canEdit: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(brand.name, style = MaterialTheme.typography.titleSmall)
            brand.nutrition?.let { NutritionSummary(it) }
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
    onClose: () -> Unit,
    onSave: (String?, String, String?, Nutrition?) -> Unit,
) {
    if (initial == null) return
    BrandEditorDialog(
        brandId = brandId,
        initial = initial,
        saving = saving,
        onDismiss = onClose,
        onSave = { name, barcode, nutrition ->
            onSave(brandId, name, barcode, nutrition)
            onClose()
        },
    )
}

@Composable
private fun BrandEditorDialog(
    brandId: String?,
    initial: BrandDraft,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String?, Nutrition?) -> Unit,
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
        onDismiss = onDismiss,
        onConfirm = { onSave(draft.name, draft.barcode.trim().ifBlank { null }, draft.toNutrition()) },
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
