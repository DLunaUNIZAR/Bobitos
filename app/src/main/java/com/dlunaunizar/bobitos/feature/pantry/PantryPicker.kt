package com.dlunaunizar.bobitos.feature.pantry

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosTopBar
import com.dlunaunizar.bobitos.core.designsystem.component.Feedback
import com.dlunaunizar.bobitos.core.designsystem.component.FeedbackSnackbarEffect
import com.dlunaunizar.bobitos.core.designsystem.component.LoadingState
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.SearchField
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.CatalogIngredient
import com.dlunaunizar.bobitos.core.model.PantryEntry

/**
 * «Añadir a la despensa» a pantalla completa: buscador sobre los ingredientes, agrupados por categoría y con casillas.
 * Lo que ya hay sale marcado y deshabilitado; si lo escrito no es ningún ingrediente, se puede apuntar tal cual.
 */
@Composable
internal fun PantryPicker(
    picker: PantryPickerState,
    ingredients: List<CatalogIngredient>,
    pantryIds: Set<String>,
    saving: Boolean,
    onQueryChange: (String) -> Unit,
    onToggle: (PantryEntry) -> Unit,
    onConfirm: () -> Unit,
    feedback: Feedback<String>?,
    onFeedbackShown: () -> Unit,
    onDismiss: () -> Unit,
) {
    val content = remember(ingredients, pantryIds, picker.query) {
        pantryPickerContent(ingredients, pantryIds, picker.query)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val snackbarHostState = remember { SnackbarHostState() }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                BobitosTopBar(
                    title = stringResource(R.string.pantry_picker_title),
                    onBack = onDismiss,
                    actions = {
                        TextButton(onClick = onConfirm, enabled = picker.selection.isNotEmpty() && !saving) {
                            Text(
                                pluralStringResource(
                                    R.plurals.pantry_picker_confirm,
                                    picker.selection.size,
                                    picker.selection.size,
                                ),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            // El diálogo tapa el snackbar de la pantalla: el error de aquí sale en el suyo.
            CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
                FeedbackSnackbarEffect(feedback, onFeedbackShown)
            }
            Column(Modifier.padding(innerPadding).fillMaxSize().padding(horizontal = Spacing.lg)) {
                SearchField(
                    query = picker.query,
                    onQueryChange = onQueryChange,
                    visible = true,
                    label = stringResource(R.string.pantry_picker_search),
                )
                if (!picker.catalogLoaded) {
                    LoadingState(Modifier.weight(1f))
                } else {
                    PickerList(content, picker.selection.keys, onToggle, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PickerList(
    content: PantryPickerContent,
    selected: Set<String>,
    onToggle: (PantryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        content.freeText?.let { row ->
            item(key = pantryFreeTextKey(row.id)) {
                PickRow(
                    label = stringResource(R.string.pantry_picker_free_text, row.entry.name),
                    checked = row.id in selected,
                    enabled = true,
                    onToggle = { onToggle(row.entry) },
                )
            }
        }
        content.groups.forEach { group ->
            item(key = pantryHeaderKey(group.category)) {
                Text(
                    text = group.category ?: stringResource(R.string.pantry_others),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                )
            }
            items(group.items, key = { pantryItemKey(it.id) }) { row ->
                PickRow(
                    label = row.entry.name,
                    checked = row.inPantry || row.id in selected,
                    enabled = !row.inPantry,
                    onToggle = { onToggle(row.entry) },
                )
            }
        }
    }
}

@Composable
private fun PickRow(label: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.sm))
    }
}
