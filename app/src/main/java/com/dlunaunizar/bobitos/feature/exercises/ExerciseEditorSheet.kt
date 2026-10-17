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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosFormSheet
import com.dlunaunizar.bobitos.core.designsystem.component.errorSupportingText
import com.dlunaunizar.bobitos.core.designsystem.component.rememberFormValidationState
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseEquipment
import com.dlunaunizar.bobitos.core.model.ExerciseInput
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.MAX_EXERCISE_DESCRIPTION_LENGTH
import com.dlunaunizar.bobitos.core.model.SetMeasure

// Hoja de alta y edición de un ejercicio del catálogo: nombre, tipo, grupo, material y descripción.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseEditorSheet(
    exercise: CatalogExercise?,
    otherIds: Set<String>,
    saving: Boolean,
    saved: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (ExerciseInput) -> Unit,
) {
    val initial = CatalogExerciseDraft.of(exercise)
    var draft by rememberSaveable(exercise?.id) { mutableStateOf(initial) }
    val errors = visibleDraftErrors(draft.errors(otherIds), saving, saved)
    val form = rememberFormValidationState()
    val nameFocus = remember { FocusRequester() }
    val muscleFocus = remember { FocusRequester() }
    val descriptionFocus = remember { FocusRequester() }
    val nameError = form.visible(errors.firstOrNull { it in NAME_ERRORS })
    val muscleError = form.visible(ExerciseDraftError.MuscleTooLong.takeIf { it in errors })
    val descriptionError = form.visible(ExerciseDraftError.DescriptionTooLong.takeIf { it in errors })
    BobitosFormSheet(
        title = stringResource(if (exercise == null) R.string.exercises_add_title else R.string.exercises_edit_title),
        confirmLabel = stringResource(R.string.save),
        saving = saving,
        dirty = { draft != initial },
        errorMessage = errorMessage,
        onDismiss = onDismiss,
        onConfirm = {
            form.submit(
                isValid = errors.isEmpty(),
                onValid = { onSave(draft.toInput()) },
                onInvalid = { firstInvalidFocus(errors, nameFocus, muscleFocus, descriptionFocus).requestFocus() },
            )
        },
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it) },
            label = { Text(stringResource(R.string.exercises_name_label)) },
            isError = nameError != null,
            supportingText = nameError?.let { { Text(stringResource(it.messageRes)) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
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
        if (draft.type.isStrength) {
            MeasureSelector(draft.measure) { draft = draft.copy(measure = it) }
        }
        OutlinedTextField(
            value = draft.muscle,
            onValueChange = { draft = draft.copy(muscle = it) },
            label = { Text(stringResource(R.string.exercises_muscle_label)) },
            isError = muscleError != null,
            supportingText = errorSupportingText(muscleError?.let { stringResource(it.messageRes) }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(muscleFocus),
        )
        EquipmentSelector(draft.equipment) { draft = draft.copy(equipment = draft.equipment.toggled(it)) }
        DescriptionField(
            value = draft.description,
            error = descriptionError,
            focus = descriptionFocus,
            onChange = { draft = draft.copy(description = it) },
        )
    }
}

@Composable
private fun DescriptionField(
    value: String,
    error: ExerciseDraftError?,
    focus: FocusRequester,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.exercises_description_label)) },
        supportingText = {
            Text(
                error?.let { stringResource(it.messageRes) }
                    ?: stringResource(
                        R.string.exercises_description_counter,
                        value.length,
                        MAX_EXERCISE_DESCRIPTION_LENGTH,
                    ),
            )
        },
        isError = error != null,
        minLines = 3,
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}

private fun firstInvalidFocus(
    errors: Set<ExerciseDraftError>,
    name: FocusRequester,
    muscle: FocusRequester,
    description: FocusRequester,
) = when {
    errors.any { it in NAME_ERRORS } -> name
    ExerciseDraftError.MuscleTooLong in errors -> muscle
    else -> description
}

private val NAME_ERRORS = setOf(
    ExerciseDraftError.NameRequired,
    ExerciseDraftError.NameTooLong,
    ExerciseDraftError.NameExists,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquipmentSelector(selected: List<ExerciseEquipment>, onToggle: (ExerciseEquipment) -> Unit) {
    Text(stringResource(R.string.exercises_equipment_label), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ExerciseEquipment.entries.forEach { option ->
            FilterChip(
                selected = option in selected,
                onClick = { onToggle(option) },
                label = { Text(stringResource(option.labelRes)) },
            )
        }
    }
}

// «Se registra en»: repeticiones o segundos; solo tiene sentido en los tipos con series.
@Composable
private fun MeasureSelector(selected: SetMeasure, onSelect: (SetMeasure) -> Unit) {
    Text(stringResource(R.string.exercises_measure_label), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SetMeasure.entries.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(stringResource(option.labelRes)) },
            )
        }
    }
}

private val SetMeasure.labelRes: Int
    get() = when (this) {
        SetMeasure.REPS -> R.string.exercises_measure_reps
        SetMeasure.SECONDS -> R.string.exercises_measure_seconds
    }

private val ExerciseDraftError.messageRes: Int
    get() = when (this) {
        ExerciseDraftError.NameRequired -> R.string.exercises_error_name_required
        ExerciseDraftError.NameTooLong -> R.string.exercises_error_name_too_long
        ExerciseDraftError.MuscleTooLong -> R.string.exercises_error_muscle_too_long
        ExerciseDraftError.DescriptionTooLong -> R.string.exercises_error_description_too_long
        ExerciseDraftError.NameExists -> R.string.exercises_error_exists
    }

// Alterna un material conservando el orden canónico (el mismo que el del enum y el de scripts/catalog).
private fun List<ExerciseEquipment>.toggled(option: ExerciseEquipment): List<ExerciseEquipment> =
    ExerciseEquipment.entries.filter { (it in this) != (it == option) }
