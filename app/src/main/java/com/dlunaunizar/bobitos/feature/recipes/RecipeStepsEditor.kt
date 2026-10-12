package com.dlunaunizar.bobitos.feature.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.RecipeLimits

// Raciones y tiempo, en una fila, del editor de recetas.
@Composable
internal fun ServingsAndTimeFields(draft: RecipeDraft, validation: RecipeUiMessage?, onDraft: (RecipeDraft) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CountField(
            value = draft.servings,
            label = stringResource(R.string.recipes_servings_label),
            error = RecipeUiMessage.ServingsInvalid.takeIf { validation == it },
            onValueChange = { onDraft(draft.copy(servings = it)) },
            modifier = Modifier.weight(1f),
        )
        CountField(
            value = draft.totalMinutes,
            label = stringResource(R.string.recipes_minutes_label),
            error = RecipeUiMessage.TotalMinutesInvalid.takeIf { validation == it },
            onValueChange = { onDraft(draft.copy(totalMinutes = it)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CountField(
    value: String,
    label: String,
    error: RecipeUiMessage?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { error?.let { Text(stringResource(it.stringResourceId)) } },
        isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

// Pasos del editor: «Paso N» con texto de varias líneas, subir, bajar y quitar; «Añadir paso» hasta 30.
@Composable
internal fun StepsEditor(draft: RecipeDraft, validation: RecipeUiMessage?, onDraft: (RecipeDraft) -> Unit) {
    // Las filas se pintan por posición: al reordenar o quitar, el foco no puede quedarse en otro paso.
    val focusManager = LocalFocusManager.current
    Text(
        text = stringResource(R.string.recipes_steps_section),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    draft.steps.forEachIndexed { index, step ->
        // Solo se marca el paso que se pasa de largo.
        val tooLong = validation == RecipeUiMessage.StepTooLong && step.trim().length > RecipeLimits.MAX_STEP_LENGTH
        Column {
            OutlinedTextField(
                value = step,
                onValueChange = { onDraft(draft.withStep(index, it)) },
                label = { Text(stringResource(R.string.recipes_step_label, index + 1)) },
                supportingText = { if (tooLong) Text(stringResource(R.string.recipes_error_step_too_long)) },
                isError = tooLong,
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        onDraft(draft.withStepMoved(index, -1))
                    },
                    enabled = index > 0,
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.recipes_step_up),
                    )
                }
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        onDraft(draft.withStepMoved(index, 1))
                    },
                    enabled = index < draft.steps.lastIndex,
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.recipes_step_down),
                    )
                }
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        onDraft(draft.withoutStep(index))
                    },
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.recipes_step_remove))
                }
            }
        }
    }
    if (draft.steps.size < RecipeLimits.MAX_STEPS) {
        TextButton(onClick = { onDraft(draft.withNewStep()) }) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(stringResource(R.string.recipes_step_add))
        }
    }
}
