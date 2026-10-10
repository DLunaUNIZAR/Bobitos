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
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseType

// Selector de ejercicio con búsqueda y filtros de tipo y grupo. La primera fila, «Personalizado…», siempre
// está: con el catálogo aún vacío (carga diferida) o sin coincidencias se puede seguir creando un ejercicio
// libre, y su nombre es lo que se buscó. La búsqueda y los filtros sobreviven a la rotación y la lista se
// actualiza sola cuando el catálogo llega.
@Composable
internal fun ExercisePickerDialog(
    catalog: List<CatalogExercise>,
    onDismiss: () -> Unit,
    onPick: (CatalogExercise) -> Unit,
    onPickCustom: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<ExerciseType?>(null) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = ExerciseFilter(query, type, group)
    val groups = remember(catalog) { catalog.muscleGroups() }
    val results = remember(catalog, filter) { catalog.filterExercises(filter) }
    val customName = customExerciseName(query)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routines_pick_exercise)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SearchField(query = query, onQueryChange = { query = it }, visible = true)
                ExerciseFilterChips(
                    groups = groups,
                    filter = filter,
                    onTypeChange = { type = it },
                    onGroupChange = { group = it },
                )
                LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 420.dp)) {
                    item(key = CUSTOM_ROW_KEY) {
                        PickerRow(
                            title = stringResource(R.string.routines_custom_exercise),
                            subtitle = customName.ifEmpty { null },
                            onClick = { onPickCustom(customName) },
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
                        val typeLabel = stringResource(exercise.type.labelRes)
                        PickerRow(
                            title = exercise.name,
                            subtitle = listOfNotNull(exercise.muscleGroup, typeLabel).joinToString(" · "),
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
            .clickable(role = Role.Button, onClick = onClick)
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
