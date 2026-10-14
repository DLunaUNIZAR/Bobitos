package com.dlunaunizar.bobitos.feature.meals

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing

// «Has cocinado …»: tachar de la compra (marcado), «¿Se ha acabado algo?» (sin marcar) y «Apuntar a la compra lo que se
// ha acabado» (marcado; solo se puede tocar si algo se ha marcado como acabado). Mientras se guarda, «Listo» no se
// puede pulsar; si falla, el error sale dentro y lo marcado se conserva (el diálogo sigue con la misma revisión).
@Composable
internal fun CookedDialog(
    review: CookedReview,
    saving: Boolean,
    errorMessage: String?,
    onConfirm: (CookedChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    var choice by remember(review) { mutableStateOf(review.defaultChoice()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.meals_cooked_title, review.mealName)) },
        text = {
            Column {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (review.crossOff.isNotEmpty()) {
                        CookedSectionTitle(R.string.meals_cooked_cross_off)
                        review.crossOff.forEach { item ->
                            CookedCheckRow(item.name, item.id in choice.crossOffIds) {
                                choice = choice.toggleCrossOff(item.id)
                            }
                        }
                    }
                    if (review.finished.isNotEmpty()) {
                        CookedSectionTitle(R.string.meals_cooked_finished)
                        review.finished.forEach { row ->
                            CookedCheckRow(row.name, row.id in choice.finishedIds) {
                                choice = choice.toggleFinished(row.id)
                            }
                        }
                        CookedCheckRow(
                            label = stringResource(R.string.meals_cooked_restock),
                            checked = choice.restock,
                            enabled = choice.finishedIds.isNotEmpty(),
                        ) { choice = choice.copy(restock = !choice.restock) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(choice) }, enabled = !saving) {
                Text(stringResource(R.string.meals_cooked_done))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CookedSectionTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
    )
}

@Composable
private fun CookedCheckRow(label: String, checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
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
