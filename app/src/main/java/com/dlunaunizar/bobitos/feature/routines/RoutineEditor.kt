package com.dlunaunizar.bobitos.feature.routines

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseSet
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import com.dlunaunizar.bobitos.feature.exercises.isStrength
import com.dlunaunizar.bobitos.feature.exercises.labelRes

internal data class RoutineEditorRequest(val routine: Routine?)

@Composable
internal fun RoutineEditor(
    request: RoutineEditorRequest,
    catalog: List<CatalogExercise>,
    isAdmin: Boolean,
    saving: Boolean,
    canWrite: Boolean,
    onDismiss: () -> Unit,
    onSave: (RoutineVisibility, String, String?, List<RoutineExercise>) -> Unit,
) {
    val routine = request.routine
    var title by remember(routine?.id) { mutableStateOf(routine?.title.orEmpty()) }
    var description by remember(routine?.id) { mutableStateOf(routine?.description.orEmpty()) }
    var global by remember(routine?.id) { mutableStateOf(routine?.visibility == RoutineVisibility.GLOBAL) }
    val exercises = remember(routine?.id) { routine.toDrafts().toMutableStateList() }
    var picking by remember { mutableStateOf(false) }
    val canChooseGlobal = isAdmin && routine == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (routine == null) R.string.routines_add_title else R.string.routines_edit_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.routines_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.routines_description_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (canChooseGlobal) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.routines_global_label), modifier = Modifier.weight(1f))
                        Switch(checked = global, onCheckedChange = { global = it })
                    }
                }
                Text(stringResource(R.string.routines_exercises_label), style = MaterialTheme.typography.titleSmall)
                exercises.forEachIndexed { index, draft ->
                    ExerciseDraftCard(draft = draft, onRemove = { exercises.removeAt(index) })
                }
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.routines_add_exercise))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && canWrite && !saving,
                onClick = {
                    val visibility = if (global) RoutineVisibility.GLOBAL else RoutineVisibility.PRIVATE
                    onSave(visibility, title, description.trim().ifBlank { null }, exercises.toExercises())
                },
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (picking) {
        ExercisePickerDialog(
            catalog = catalog,
            onDismiss = { picking = false },
            onPick = { catalogExercise ->
                exercises.add(
                    if (catalogExercise == null) {
                        ExerciseDraft()
                    } else {
                        ExerciseDraft(
                            name = catalogExercise.name,
                            exerciseId = catalogExercise.id,
                            type = catalogExercise.type,
                        )
                    },
                )
                picking = false
            },
        )
    }
}

@Composable
private fun ExerciseDraftCard(draft: ExerciseDraft, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft.name = it },
                    label = { Text(stringResource(R.string.routines_exercise_name_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.routines_remove_exercise))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                ExerciseType.entries.forEach { option ->
                    FilterChip(
                        selected = draft.type == option,
                        onClick = { draft.type = option },
                        label = { Text(stringResource(option.labelRes)) },
                    )
                }
            }
            if (draft.type.isStrength) {
                StrengthSets(draft)
            } else {
                CardioFields(draft)
            }
        }
    }
}

@Composable
private fun StrengthSets(draft: ExerciseDraft) {
    Text(stringResource(R.string.routines_sets_label), style = MaterialTheme.typography.labelLarge)
    draft.sets.forEachIndexed { index, set ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            OutlinedTextField(
                value = set.reps,
                onValueChange = { set.reps = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.routines_reps_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = set.weight,
                onValueChange = { set.weight = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                label = { Text(stringResource(R.string.routines_weight_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { draft.sets.removeAt(index) }) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.routines_remove_set))
            }
        }
    }
    TextButton(onClick = { draft.sets.add(SetDraft()) }) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.routines_add_set))
    }
}

@Composable
private fun CardioFields(draft: ExerciseDraft) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        OutlinedTextField(
            value = draft.duration,
            onValueChange = { draft.duration = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.routines_duration_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = draft.level,
            onValueChange = { draft.level = it },
            label = { Text(stringResource(R.string.routines_level_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ExercisePickerDialog(
    catalog: List<CatalogExercise>,
    onDismiss: () -> Unit,
    onPick: (CatalogExercise?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routines_pick_exercise)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                TextButton(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth()) { Text(stringResource(R.string.routines_custom_exercise)) }
                }
                catalog.forEach { exercise ->
                    TextButton(onClick = { onPick(exercise) }, modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth()) {
                            Text("${exercise.name} · ${stringResource(exercise.type.labelRes)}")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// --- Borradores observables del editor ---

private class SetDraft(reps: String = "", weight: String = "") {
    var reps by mutableStateOf(reps)
    var weight by mutableStateOf(weight)
}

private class ExerciseDraft(
    name: String = "",
    val exerciseId: String? = null,
    type: ExerciseType = ExerciseType.MAQUINA,
    sets: List<SetDraft> = emptyList(),
    duration: String = "",
    level: String = "",
) {
    var name by mutableStateOf(name)
    var type by mutableStateOf(type)
    val sets: SnapshotStateList<SetDraft> = sets.toMutableStateList()
    var duration by mutableStateOf(duration)
    var level by mutableStateOf(level)
}

private fun Routine?.toDrafts(): List<ExerciseDraft> = this?.exercises.orEmpty().map { exercise ->
    ExerciseDraft(
        name = exercise.name,
        exerciseId = exercise.exerciseId,
        type = exercise.type,
        sets = exercise.sets.map { SetDraft(it.reps?.toString().orEmpty(), it.weight?.let(::formatWeight).orEmpty()) },
        duration = exercise.durationMinutes?.toString().orEmpty(),
        level = exercise.level.orEmpty(),
    )
}

private fun List<ExerciseDraft>.toExercises(): List<RoutineExercise> = filter { it.name.isNotBlank() }.map { draft ->
    RoutineExercise(
        name = draft.name.trim(),
        exerciseId = draft.exerciseId,
        type = draft.type,
        sets = if (draft.type.isStrength) {
            // El teclado decimal en locale es-ES emite coma; se normaliza a punto antes de parsear.
            draft.sets.map {
                ExerciseSet(reps = it.reps.toIntOrNull(), weight = it.weight.replace(',', '.').toDoubleOrNull())
            }
        } else {
            emptyList()
        },
        durationMinutes = if (draft.type.isStrength) null else draft.duration.toIntOrNull(),
        level = if (draft.type.isStrength) null else draft.level.trim().ifBlank { null },
    )
}

private fun formatWeight(weight: Double): String =
    if (weight % 1.0 == 0.0) weight.toLong().toString() else weight.toString()
