package com.dlunaunizar.bobitos.feature.routines

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import com.dlunaunizar.bobitos.feature.exercises.ExerciseDraftListSaver
import com.dlunaunizar.bobitos.feature.exercises.ExerciseListEditor
import com.dlunaunizar.bobitos.feature.exercises.toExerciseDrafts
import com.dlunaunizar.bobitos.feature.exercises.toRoutineExercises

@Composable
internal fun RoutineEditor(
    routine: Routine?,
    catalog: List<CatalogExercise>,
    onCatalogNeeded: () -> Unit,
    isAdmin: Boolean,
    saving: Boolean,
    errorMessage: String?,
    canWrite: Boolean,
    onDismiss: () -> Unit,
    onSave: (RoutineVisibility, String, String?, List<RoutineExercise>) -> Unit,
) {
    val initial = RoutineDraft.of(routine)
    val initialExercises = remember(routine?.id) {
        routine?.exercises.orEmpty().toExerciseDrafts().toRoutineExercises()
    }
    // Borrador guardable: campos simples y lista de ejercicios (con sus series), para sobrevivir a una rotación.
    var draft by rememberSaveable(routine?.id) { mutableStateOf(initial) }
    val exercises = rememberSaveable(routine?.id, saver = ExerciseDraftListSaver) {
        routine?.exercises.orEmpty().toExerciseDrafts().toMutableStateList()
    }
    val canChooseGlobal = isAdmin && routine == null

    BobitosFormSheet(
        title = stringResource(if (routine == null) R.string.routines_add_title else R.string.routines_edit_title),
        confirmLabel = stringResource(R.string.save),
        confirmEnabled = draft.title.isNotBlank() && canWrite,
        saving = saving,
        dirty = { draft != initial || exercises.toRoutineExercises() != initialExercises },
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = {
            val visibility = if (draft.global) RoutineVisibility.GLOBAL else RoutineVisibility.PRIVATE
            onSave(visibility, draft.title, draft.description.trim().ifBlank { null }, exercises.toRoutineExercises())
        },
    ) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = { draft = draft.copy(title = it) },
            label = { Text(stringResource(R.string.routines_title_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.description,
            onValueChange = { draft = draft.copy(description = it) },
            label = { Text(stringResource(R.string.routines_description_label)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        if (canChooseGlobal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.routines_global_label), modifier = Modifier.weight(1f))
                Switch(checked = draft.global, onCheckedChange = { draft = draft.copy(global = it) })
            }
        }
        Text(stringResource(R.string.routines_exercises_label), style = MaterialTheme.typography.titleSmall)
        ExerciseListEditor(drafts = exercises, catalog = catalog, onCatalogNeeded = onCatalogNeeded)
    }
}
