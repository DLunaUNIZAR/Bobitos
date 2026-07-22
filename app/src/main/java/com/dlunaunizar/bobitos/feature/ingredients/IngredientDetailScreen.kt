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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.R
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

    var showFichaEditor by remember { mutableStateOf(false) }
    var brandEditor by remember { mutableStateOf<BrandEditorRequest?>(null) }
    var confirmDeleteIngredient by remember { mutableStateOf(false) }
    var brandToDelete by remember { mutableStateOf<IngredientBrand?>(null) }
    val ingredient = state.ingredient
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Un escaneo con éxito abre el editor de marca prerrellenado.
    LaunchedEffect(state.scannedBrand) {
        state.scannedBrand?.let { draft ->
            brandEditor = BrandEditorRequest(null, draft.name, draft.barcode, draft.nutrition)
            viewModel.consumeScannedBrand()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(ingredient?.name ?: stringResource(R.string.ingredients_title)) },
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
                    onAdd = { brandEditor = BrandEditorRequest(null, "", "", null) },
                    onScan = { scope.launch { scanBarcode(context)?.let(viewModel::lookupBarcode) } },
                    onEdit = { brandEditor = BrandEditorRequest(it.id, it.name, it.barcode.orEmpty(), it.nutrition) },
                    onDelete = { brandToDelete = it },
                )
            }
        }
    }

    if (showFichaEditor && ingredient != null) {
        IngredientEditorDialog(
            ingredient = ingredient,
            saving = state.isSaving,
            onDismiss = { showFichaEditor = false },
            onSave = { name, category, unit ->
                viewModel.updateIngredient(name, category, unit)
                showFichaEditor = false
            },
        )
    }

    brandEditor?.let { request ->
        BrandEditorDialog(
            request = request,
            saving = state.isSaving,
            onDismiss = { brandEditor = null },
            onSave = { name, barcode, nutrition ->
                if (request.brandId == null) {
                    viewModel.addBrand(name, barcode, nutrition)
                } else {
                    viewModel.updateBrand(request.brandId, name, barcode, nutrition)
                }
                brandEditor = null
            },
        )
    }

    if (confirmDeleteIngredient && ingredient != null) {
        ConfirmDialog(
            title = stringResource(R.string.ingredients_delete_title),
            body = stringResource(R.string.ingredients_delete_body, ingredient.name),
            confirm = stringResource(R.string.ingredients_delete),
            enabled = !state.isSaving,
            onConfirm = {
                viewModel.deleteIngredient()
                confirmDeleteIngredient = false
            },
            onDismiss = { confirmDeleteIngredient = false },
        )
    }

    brandToDelete?.let { brand ->
        ConfirmDialog(
            title = stringResource(R.string.ingredients_brand_delete_title),
            body = stringResource(R.string.ingredients_brand_delete_body, brand.name),
            confirm = stringResource(R.string.ingredients_delete),
            enabled = !state.isSaving,
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
        nutrition.energyKcal?.let { stringResource(R.string.nutrition_energy, formatNumber(it)) },
        nutrition.fat?.let { stringResource(R.string.nutrition_fat, formatNumber(it)) },
        nutrition.carbohydrates?.let { stringResource(R.string.nutrition_carbs, formatNumber(it)) },
        nutrition.sugars?.let { stringResource(R.string.nutrition_sugars, formatNumber(it)) },
        nutrition.protein?.let { stringResource(R.string.nutrition_protein, formatNumber(it)) },
        nutrition.salt?.let { stringResource(R.string.nutrition_salt, formatNumber(it)) },
    )
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BrandEditorDialog(
    request: BrandEditorRequest,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String?, Nutrition?) -> Unit,
) {
    var name by remember(request) { mutableStateOf(request.name) }
    var barcode by remember(request) { mutableStateOf(request.barcode) }
    val fields = remember(request) { NutritionDraft(request.nutrition) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (request.brandId == null) {
                        R.string.ingredients_brand_add_title
                    } else {
                        R.string.ingredients_brand_edit_title
                    },
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ingredients_brand_name_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text(stringResource(R.string.ingredients_brand_barcode_label)) },
                    singleLine = true,
                )
                Text(
                    stringResource(R.string.nutrition_section),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                fields.rows.forEach { row ->
                    OutlinedTextField(
                        value = row.value,
                        onValueChange = { row.value = it },
                        label = { Text(stringResource(row.labelRes)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !saving,
                onClick = { onSave(name, barcode.trim().ifBlank { null }, fields.toNutrition()) },
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(enabled = enabled, onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private data class BrandEditorRequest(
    val brandId: String?,
    val name: String,
    val barcode: String,
    val nutrition: Nutrition?,
)

// Estado editable de los 6 campos nutricionales (texto), con su etiqueta.
private class NutritionRow(val labelRes: Int, initial: Double?) {
    var value by mutableStateOf(initial?.let(::formatNumber).orEmpty())
}

private class NutritionDraft(nutrition: Nutrition?) {
    val energy = NutritionRow(R.string.nutrition_energy_label, nutrition?.energyKcal)
    val fat = NutritionRow(R.string.nutrition_fat_label, nutrition?.fat)
    val carbs = NutritionRow(R.string.nutrition_carbs_label, nutrition?.carbohydrates)
    val sugars = NutritionRow(R.string.nutrition_sugars_label, nutrition?.sugars)
    val protein = NutritionRow(R.string.nutrition_protein_label, nutrition?.protein)
    val salt = NutritionRow(R.string.nutrition_salt_label, nutrition?.salt)
    val rows = listOf(energy, fat, carbs, sugars, protein, salt)

    fun toNutrition(): Nutrition? {
        val nutrition = Nutrition(
            energyKcal = parseNutritionValue(energy.value),
            fat = parseNutritionValue(fat.value),
            carbohydrates = parseNutritionValue(carbs.value),
            sugars = parseNutritionValue(sugars.value),
            protein = parseNutritionValue(protein.value),
            salt = parseNutritionValue(salt.value),
        )
        return nutrition.takeUnless(Nutrition::isEmpty)
    }
}

// Formatea sin decimales innecesarios («120», «1.5»).
private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
