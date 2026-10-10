package com.dlunaunizar.bobitos.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosInfoSheet
import com.dlunaunizar.bobitos.core.designsystem.component.InfoChip
import com.dlunaunizar.bobitos.core.designsystem.component.rememberSafeLinks
import com.dlunaunizar.bobitos.core.designsystem.theme.Spacing
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.SetMeasure

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
            InfoChip(stringResource(exercise.type.labelRes), contentColor = exercise.type.accent())
            exercise.muscleGroup?.let { InfoChip(it) }
            if (exercise.measure == SetMeasure.SECONDS) {
                InfoChip(stringResource(R.string.exercises_detail_by_time))
            }
        }
        if (exercise.equipment.isNotEmpty()) {
            val names = exercise.equipment.map { stringResource(it.labelRes) }.joinToString(", ")
            Text(
                text = stringResource(R.string.exercises_detail_equipment, names),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        exercise.image?.let { ExerciseImageBlock(it, exercise.name) }
        Text(
            text = exercise.description ?: stringResource(R.string.exercises_detail_no_description),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = Spacing.sm),
        )
        exercise.source?.let {
            Text(text = attributionText(it.attribution()), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// Texto de atribución según el proveedor, con enlaces seguros:
// wger «Texto adaptado de wger.de (ejercicio n.º N) · Autoría: X · Licencia CC BY-SA 4.0»;
// bobitos «Texto original del Catálogo Bobitos · Licencia …» (sin ficha externa); otros «Fuente: X · Licencia …».
@Composable
private fun attributionText(attribution: ExerciseAttribution): AnnotatedString {
    val link = rememberSafeLinks()
    val licensePrefix = stringResource(R.string.exercises_attribution_license)
    return when (attribution) {
        is ExerciseAttribution.Wger -> {
            val origin = stringResource(R.string.exercises_attribution_origin, attribution.sourceId)
            val prefix = stringResource(R.string.exercises_attribution_prefix)
            val author = attribution.author?.let { stringResource(R.string.exercises_attribution_author, it) }
            buildAnnotatedString {
                append(prefix)
                attribution.url?.let { withLink(link(it)) { append(origin) } } ?: append(origin)
                author?.let { append(it) }
                append(licensePrefix)
                appendLicense(attribution.license, link)
            }
        }
        is ExerciseAttribution.Bobitos -> buildAnnotatedString {
            append(stringResource(R.string.exercises_attribution_bobitos))
            append(licensePrefix)
            appendLicense(attribution.license, link)
        }
        is ExerciseAttribution.Other -> buildAnnotatedString {
            append(stringResource(R.string.exercises_attribution_other, attribution.provider))
            append(licensePrefix)
            appendLicense(attribution.license, link)
        }
    }
}

private fun AnnotatedString.Builder.appendLicense(code: String, link: (String) -> LinkAnnotation.Url) {
    val label = licenseLabel(code)
    licenseUrl(code)?.let { withLink(link(it)) { append(label) } } ?: append(label)
}
