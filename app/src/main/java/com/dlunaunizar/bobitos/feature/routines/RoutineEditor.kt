package com.dlunaunizar.bobitos.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import com.dlunaunizar.bobitos.feature.exercises.ExerciseListEditor
import com.dlunaunizar.bobitos.feature.exercises.toExerciseDrafts
import com.dlunaunizar.bobitos.feature.exercises.toRoutineExercises

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
    val exercises = remember(routine?.id) { routine?.exercises.orEmpty().toExerciseDrafts().toMutableStateList() }
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
                ExerciseListEditor(drafts = exercises, catalog = catalog)
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && canWrite && !saving,
                onClick = {
                    val visibility = if (global) RoutineVisibility.GLOBAL else RoutineVisibility.PRIVATE
                    onSave(visibility, title, description.trim().ifBlank { null }, exercises.toRoutineExercises())
                },
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
