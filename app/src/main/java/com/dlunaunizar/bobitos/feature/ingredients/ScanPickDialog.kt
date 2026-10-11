package com.dlunaunizar.bobitos.feature.ingredients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.component.SearchField
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.CatalogIngredient

/**
 * «¿De qué ingrediente es?» tras escanear: el buscador empieza vacío y, mientras siga así, debajo salen las
 * sugerencias calculadas del nombre del producto; al escribir, la búsqueda normal sobre [ingredients].
 */
@Composable
internal fun ScanPickDialog(
    ingredients: List<CatalogIngredient>,
    productName: String,
    onPick: (CatalogIngredient) -> Unit,
    onCreatePersonal: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val suggestions = remember(ingredients, productName) { suggestIngredients(ingredients, productName) }
    val shown = remember(ingredients, query, suggestions) {
        if (query.isBlank()) suggestions else ingredients.matching(query)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ingredients_scan_pick_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    visible = true,
                    label = stringResource(R.string.ingredients_search_label),
                )
                if (query.isBlank() && suggestions.isNotEmpty()) {
                    Text(
                        stringResource(R.string.ingredients_scan_suggestions),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(shown, key = CatalogIngredient::id) { ingredient ->
                        Text(
                            text = ingredient.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onPick(ingredient) }
                                .padding(vertical = Spacing.sm),
                        )
                    }
                }
                OutlinedButton(onClick = onCreatePersonal, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ingredients_scan_create_personal))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
