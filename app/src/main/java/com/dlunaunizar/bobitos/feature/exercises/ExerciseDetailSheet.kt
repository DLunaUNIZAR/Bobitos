package com.dlunaunizar.bobitos.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosInfoSheet
import com.dlunaunizar.bobitos.core.designsystem.component.rememberSafeLinks
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseSource

// Ficha de solo lectura de un ejercicio: tipo y grupo, material, descripción y atribución de la fuente.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseDetailSheet(
    exercise: CatalogExercise,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    BobitosInfoSheet(
        title = exercise.name,
        onDismiss = onDismiss,
        actions = {
            if (canEdit) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.exercises_delete)) }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.exercises_edit)) }
            }
        },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(stringResource(exercise.type.labelRes)) },
                colors = AssistChipDefaults.assistChipColors(disabledLabelColor = exercise.type.accent()),
            )
            exercise.muscleGroup?.let {
                AssistChip(onClick = {}, enabled = false, label = { Text(it) })
            }
        }
        if (exercise.equipment.isNotEmpty()) {
            val names = exercise.equipment.map { stringResource(it.labelRes) }.joinToString(", ")
            Text(
                text = stringResource(R.string.exercises_detail_equipment, names),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = exercise.description ?: stringResource(R.string.exercises_detail_no_description),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = Spacing.sm),
        )
        exercise.source?.let { Text(text = attributionText(it), style = MaterialTheme.typography.bodySmall) }
    }
}

// «Texto adaptado de wger.de (ejercicio n.º N) · Autoría: X · Licencia CC BY-SA 4.0», con enlaces seguros.
@Composable
private fun attributionText(source: ExerciseSource): AnnotatedString {
    val link = rememberSafeLinks()
    val origin = stringResource(R.string.exercises_attribution_origin, source.sourceId)
    val prefix = stringResource(R.string.exercises_attribution_prefix)
    val author = source.author?.let { stringResource(R.string.exercises_attribution_author, it) }
    val licensePrefix = stringResource(R.string.exercises_attribution_license)
    val licenseText = licenseLabel(source.license)
    return buildAnnotatedString {
        append(prefix)
        source.linkUrl()?.let { withLink(link(it)) { append(origin) } } ?: append(origin)
        author?.let { append(it) }
        append(licensePrefix)
        licenseUrl(source.license)?.let { withLink(link(it)) { append(licenseText) } } ?: append(licenseText)
    }
}
