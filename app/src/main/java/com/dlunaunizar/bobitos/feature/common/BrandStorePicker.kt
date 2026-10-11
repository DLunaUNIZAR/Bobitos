package com.dlunaunizar.bobitos.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.IngredientBrand
import com.dlunaunizar.bobitos.core.model.Supermarket
import com.dlunaunizar.bobitos.feature.shopping.SupermarketDropdown

/**
 * Marca y tienda del ingrediente reconocido (§5): dos desplegables de igual ancho. Sin marcas
 * registradas, la marca es un campo de texto libre.
 */
@Composable
internal fun BrandStorePicker(
    context: IngredientShoppingContext,
    selection: BrandStoreSelection,
    freeBrand: String,
    onBrandSelected: (String?) -> Unit,
    onStoreSelected: (Supermarket?) -> Unit,
    onFreeBrandChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (context.brands.isEmpty()) {
                OutlinedTextField(
                    value = freeBrand,
                    onValueChange = onFreeBrandChange,
                    label = { Text(stringResource(R.string.shopping_brand_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PickerLabel(R.string.shopping_brand_picker_label)
                BrandDropdown(context.brands, selection.brandId, onBrandSelected)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PickerLabel(R.string.shopping_store_picker_label)
            SupermarketDropdown(
                selected = selection.store,
                onSelect = onStoreSelected,
                options = context.storeOptions(selection),
            )
        }
    }
}

@Composable
private fun PickerLabel(label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BrandDropdown(brands: List<IngredientBrand>, selectedId: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val indifferent = stringResource(R.string.shopping_brand_indifferent)
    val current = brands.firstOrNull { it.id == selectedId }?.name ?: indifferent
    val description = stringResource(R.string.shopping_brand_picker_description, current)
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        ) {
            Text(text = current, modifier = Modifier.weight(1f), maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            brands.forEach { brand ->
                DropdownMenuItem(
                    text = { Text(if (brand.favorite) "★ ${brand.name}" else brand.name) },
                    onClick = {
                        onSelect(brand.id)
                        expanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(indifferent) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
        }
    }
}
