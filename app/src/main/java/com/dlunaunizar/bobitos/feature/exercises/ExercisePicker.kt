package com.dlunaunizar.bobitos.feature.exercises

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.prepareQuery
import com.dlunaunizar.bobitos.core.designsystem.component.SearchField
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.CatalogExercise

// Filtra el catálogo del selector por nombre o grupo muscular (sin tildes ni mayúsculas), conservando el
// orden. Una consulta en blanco devuelve todo el catálogo.
internal fun filterExercisePicker(catalog: List<CatalogExercise>, query: String): List<CatalogExercise> {
    val prepared = prepareQuery(query)
    return catalog.filter { prepared.matches(it.name, it.muscleGroup) }
}

// Selector de ejercicio con búsqueda. La primera fila, «Personalizado…», siempre está: con el catálogo aún
// vacío (carga diferida) o sin coincidencias se puede seguir creando un ejercicio libre. La consulta
// sobrevive a la rotación y la lista se actualiza sola cuando el catálogo llega.
@Composable
internal fun ExercisePickerDialog(
    catalog: List<CatalogExercise>,
    onDismiss: () -> Unit,
    onPick: (CatalogExercise?) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = filterExercisePicker(catalog, query)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routines_pick_exercise)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SearchField(query = query, onQueryChange = { query = it }, visible = true)
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item(key = CUSTOM_ROW_KEY) {
                        PickerRow(
                            title = stringResource(R.string.routines_custom_exercise),
                            subtitle = null,
                            onClick = { onPick(null) },
                        )
                    }
                    if (results.isEmpty() && catalog.isNotEmpty()) {
                        item(key = EMPTY_ROW_KEY) {
                            Text(
                                text = stringResource(R.string.exercises_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Spacing.sm),
                            )
                        }
                    }
                    items(results, key = CatalogExercise::id) { exercise ->
                        val type = stringResource(exercise.type.labelRes)
                        PickerRow(
                            title = exercise.name,
                            subtitle = listOfNotNull(exercise.muscleGroup, type).joinToString(" · "),
                            onClick = { onPick(exercise) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// Clave fija de las filas que no son ejercicios; los ids del catálogo son slugs y nunca llevan «:».
private const val CUSTOM_ROW_KEY = ":custom"
private const val EMPTY_ROW_KEY = ":empty"

@Composable
private fun PickerRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
