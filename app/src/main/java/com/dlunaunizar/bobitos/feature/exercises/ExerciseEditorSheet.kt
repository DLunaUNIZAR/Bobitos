package com.dlunaunizar.bobitos.feature.exercises

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseEquipment
import com.dlunaunizar.bobitos.core.model.ExerciseInput
import com.dlunaunizar.bobitos.core.model.ExerciseType

private const val MAX_DESCRIPTION = 2000

// Hoja de alta y edición de un ejercicio del catálogo: nombre, tipo, grupo, material y descripción.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseEditorSheet(
    exercise: CatalogExercise?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ExerciseInput) -> Unit,
) {
    val initial = CatalogExerciseDraft.of(exercise)
    var draft by rememberSaveable(exercise?.id) { mutableStateOf(initial) }
    BobitosFormSheet(
        title = stringResource(if (exercise == null) R.string.exercises_add_title else R.string.exercises_edit_title),
        confirmLabel = stringResource(R.string.save),
        confirmEnabled = draft.name.isNotBlank() && draft.description.length <= MAX_DESCRIPTION,
        saving = saving,
        dirty = { draft != initial },
        onDismiss = onDismiss,
        onConfirm = { onSave(draft.toInput()) },
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it) },
            label = { Text(stringResource(R.string.exercises_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.exercises_type_label), style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ExerciseType.entries.forEach { option ->
                FilterChip(
                    selected = draft.type == option,
                    onClick = { draft = draft.copy(type = option) },
                    label = { Text(stringResource(option.labelRes)) },
                )
            }
        }
        OutlinedTextField(
            value = draft.muscle,
            onValueChange = { draft = draft.copy(muscle = it) },
            label = { Text(stringResource(R.string.exercises_muscle_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.exercises_equipment_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ExerciseEquipment.entries.forEach { option ->
                FilterChip(
                    selected = option in draft.equipment,
                    onClick = { draft = draft.copy(equipment = draft.equipment.toggled(option)) },
                    label = { Text(stringResource(option.labelRes)) },
                )
            }
        }
        OutlinedTextField(
            value = draft.description,
            onValueChange = { draft = draft.copy(description = it) },
            label = { Text(stringResource(R.string.exercises_description_label)) },
            supportingText = {
                Text(stringResource(R.string.exercises_description_counter, draft.description.length, MAX_DESCRIPTION))
            },
            isError = draft.description.length > MAX_DESCRIPTION,
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Alterna un material conservando el orden canónico (el mismo que el del enum y el de scripts/catalog).
private fun List<ExerciseEquipment>.toggled(option: ExerciseEquipment): List<ExerciseEquipment> =
    ExerciseEquipment.entries.filter { (it in this) != (it == option) }
