package com.dlunaunizar.bobitos.feature.exercises

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.formatDecimal
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseSet
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.SetMeasure

// Editor reutilizable de una lista de ejercicios (drafts observables), compartido por el catálogo de
// Rutinas (definir la plantilla) y por la sesión de una actividad de gimnasio (registrar lo hecho).
// Los campos dependen del tipo: fuerza → filas de series (reps + peso); cardio → minutos + nivel.
// La lista [drafts] la posee y recuerda el llamante; aquí solo se muta (añadir/quitar) y se pinta.
@Composable
internal fun ExerciseListEditor(
    drafts: SnapshotStateList<ExerciseDraft>,
    catalog: List<CatalogExercise>,
    onCatalogNeeded: () -> Unit,
) {
    // El catálogo se carga de forma diferida: el editor lo pide al aparecer y el selector se rellena solo.
    LaunchedEffect(Unit) { onCatalogNeeded() }
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        drafts.forEachIndexed { index, draft ->
            ExerciseDraftCard(draft = draft, onRemove = { drafts.removeAt(index) })
        }
        // Siempre abre el selector: su primera fila, «Personalizado…», cubre el catálogo vacío o aún sin llegar.
        OutlinedButton(
            onClick = { picking = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.routines_add_exercise))
        }
    }

    if (picking) {
        ExercisePickerDialog(
            catalog = catalog,
            onDismiss = { picking = false },
            onPick = { catalogExercise ->
                drafts.add(catalogExercise.toExerciseDraft())
                picking = false
            },
            onPickCustom = { name ->
                drafts.add(ExerciseDraft(name = name))
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
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { draft.notes = it },
                label = { Text(stringResource(R.string.routines_notes_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StrengthSets(draft: ExerciseDraft) {
    Text(stringResource(R.string.routines_sets_label), style = MaterialTheme.typography.labelLarge)
    val measures = SetMeasure.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        measures.forEachIndexed { position, option ->
            SegmentedButton(
                selected = draft.measure == option,
                onClick = { draft.measure = option },
                shape = SegmentedButtonDefaults.itemShape(index = position, count = measures.size),
            ) {
                Text(
                    stringResource(
                        if (option == SetMeasure.SECONDS) {
                            R.string.exercises_measure_seconds
                        } else {
                            R.string.exercises_measure_reps
                        },
                    ),
                )
            }
        }
    }
    val timed = draft.measure == SetMeasure.SECONDS
    draft.sets.forEachIndexed { index, set ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            OutlinedTextField(
                value = if (timed) set.seconds else set.reps,
                onValueChange = {
                    val digits = it.filter(Char::isDigit)
                    if (timed) set.seconds = digits else set.reps = digits
                },
                label = {
                    Text(stringResource(if (timed) R.string.routines_seconds_label else R.string.routines_reps_label))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = set.weight,
                onValueChange = { set.weight = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                label = { Text(stringResource(draft.type.weightLabelRes)) },
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

// --- Borradores observables ---

internal class SetDraft(reps: String = "", weight: String = "", seconds: String = "") {
    var reps by mutableStateOf(reps)
    var seconds by mutableStateOf(seconds)
    var weight by mutableStateOf(weight)
}

internal class ExerciseDraft(
    name: String = "",
    val exerciseId: String? = null,
    type: ExerciseType = ExerciseType.MAQUINA,
    sets: List<SetDraft> = emptyList(),
    duration: String = "",
    level: String = "",
    notes: String = "",
    measure: SetMeasure = SetMeasure.REPS,
) {
    var measure by mutableStateOf(measure)
    var name by mutableStateOf(name)
    var type by mutableStateOf(type)
    val sets: SnapshotStateList<SetDraft> = sets.toMutableStateList()
    var duration by mutableStateOf(duration)
    var level by mutableStateOf(level)
    var notes by mutableStateOf(notes)
}

// Borrador nuevo desde una ficha del catálogo: hereda nombre, tipo y la medida de sus series.
internal fun CatalogExercise.toExerciseDraft(): ExerciseDraft =
    ExerciseDraft(name = name, exerciseId = id, type = type, measure = measure)

internal fun List<RoutineExercise>.toExerciseDrafts(): List<ExerciseDraft> = map { exercise ->
    ExerciseDraft(
        name = exercise.name,
        exerciseId = exercise.exerciseId,
        type = exercise.type,
        measure = exercise.measure,
        sets = exercise.sets.map {
            SetDraft(
                reps = it.reps?.toString().orEmpty(),
                weight = it.weight?.let(::formatDecimal).orEmpty(),
                seconds = it.seconds?.toString().orEmpty(),
            )
        },
        duration = exercise.durationMinutes?.toString().orEmpty(),
        level = exercise.level.orEmpty(),
        notes = exercise.notes.orEmpty(),
    )
}

internal fun List<ExerciseDraft>.toRoutineExercises(): List<RoutineExercise> =
    filter { it.name.isNotBlank() }.map { draft ->
        val timed = draft.type.isStrength && draft.measure == SetMeasure.SECONDS
        RoutineExercise(
            name = draft.name.trim(),
            exerciseId = draft.exerciseId,
            type = draft.type,
            sets = if (draft.type.isStrength) {
                // El teclado decimal en locale es-ES emite coma; se normaliza a punto antes de parsear.
                draft.sets.map {
                    val weight = it.weight.replace(',', '.').toDoubleOrNull()
                    if (timed) {
                        ExerciseSet(weight = weight, seconds = it.seconds.toIntOrNull())
                    } else {
                        ExerciseSet(reps = it.reps.toIntOrNull(), weight = weight)
                    }
                }
            } else {
                emptyList()
            },
            durationMinutes = if (draft.type.isStrength) null else draft.duration.toIntOrNull(),
            level = if (draft.type.isStrength) null else draft.level.trim().ifBlank { null },
            notes = draft.notes.trim().ifBlank { null },
            measure = if (timed) SetMeasure.SECONDS else SetMeasure.REPS,
        )
    }
