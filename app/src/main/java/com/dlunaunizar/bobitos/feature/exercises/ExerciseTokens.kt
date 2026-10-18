package com.dlunaunizar.bobitos.feature.exercises

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sports
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.LocalDarkTheme
import com.dlunaunizar.bobitos.core.designsystem.theme.themedColor
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseEquipment
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.recordsSets

@get:StringRes
internal val ExerciseType.labelRes: Int
    get() = when (this) {
        ExerciseType.MAQUINA -> R.string.exercise_type_machine
        ExerciseType.PESO_LIBRE -> R.string.exercise_type_free_weight
        ExerciseType.PESO_CORPORAL -> R.string.exercise_type_bodyweight
        ExerciseType.CARDIO -> R.string.exercise_type_cardio
        ExerciseType.OTROS -> R.string.exercise_type_other
    }

// Cierto si el tipo registra series frente a tiempo/nivel (cardio); la lista vive en core/model.
internal val ExerciseType.isStrength: Boolean
    get() = recordsSets

// En peso corporal la carga es un lastre opcional, no el peso levantado.
@get:StringRes
internal val ExerciseType.weightLabelRes: Int
    get() = if (this == ExerciseType.PESO_CORPORAL) R.string.routines_ballast_label else R.string.routines_weight_label

@get:StringRes
internal val ExerciseEquipment.labelRes: Int
    get() = when (this) {
        ExerciseEquipment.BARRA -> R.string.exercise_equipment_barbell
        ExerciseEquipment.BARRA_Z -> R.string.exercise_equipment_ez_bar
        ExerciseEquipment.MANCUERNAS -> R.string.exercise_equipment_dumbbells
        ExerciseEquipment.KETTLEBELL -> R.string.exercise_equipment_kettlebell
        ExerciseEquipment.DISCO -> R.string.exercise_equipment_plate
        ExerciseEquipment.POLEA -> R.string.exercise_equipment_cable
        ExerciseEquipment.MAQUINA -> R.string.exercise_equipment_machine
        ExerciseEquipment.BANCO -> R.string.exercise_equipment_bench
        ExerciseEquipment.BANCO_INCLINADO -> R.string.exercise_equipment_incline_bench
        ExerciseEquipment.BARRA_DOMINADAS -> R.string.exercise_equipment_pullup_bar
        ExerciseEquipment.ESTERILLA -> R.string.exercise_equipment_mat
        ExerciseEquipment.FITBALL -> R.string.exercise_equipment_fitball
        ExerciseEquipment.BANDA_ELASTICA -> R.string.exercise_equipment_band
    }

internal fun ExerciseType.accent(dark: Boolean): Color = when (this) {
    ExerciseType.MAQUINA -> themedColor(dark, 0xFF1257A5, 0xFF85B8F2)
    ExerciseType.PESO_LIBRE -> themedColor(dark, 0xFF691B98, 0xFFD2A2EF)
    ExerciseType.PESO_CORPORAL -> themedColor(dark, 0xFF256528, 0xFF68C76D)
    ExerciseType.CARDIO -> themedColor(dark, 0xFF904019, 0xFFE9A481)
    ExerciseType.OTROS -> themedColor(dark, 0xFF595959, 0xFFB4B4B4)
}

@Composable
internal fun ExerciseType.accent(): Color = accent(LocalDarkTheme.current)

// Icono del tipo: lo que se ve en la miniatura de la lista cuando el ejercicio no tiene imagen.
internal val ExerciseType.icon: ImageVector
    get() = when (this) {
        ExerciseType.MAQUINA -> Icons.Rounded.Settings
        ExerciseType.PESO_LIBRE -> Icons.Rounded.FitnessCenter
        ExerciseType.PESO_CORPORAL -> Icons.Rounded.SelfImprovement
        ExerciseType.CARDIO -> Icons.Rounded.DirectionsRun
        ExerciseType.OTROS -> Icons.Rounded.Sports
    }

// Textos de la línea secundaria de la fila («Peso libre · Mancuernas»): el tipo y después el material.
internal fun exerciseSupportingRes(exercise: CatalogExercise): List<Int> =
    listOf(exercise.type.labelRes) + exercise.equipment.map { it.labelRes }

// La línea secundaria de la fila: el grupo muscular (si se muestra y lo hay) y después el tipo y el material.
internal fun exerciseSupportingLine(muscleGroup: String?, labels: List<String>, showMuscleGroup: Boolean): String =
    listOfNotNull(muscleGroup?.trim()?.takeIf { showMuscleGroup && it.isNotEmpty() })
        .plus(labels)
        .joinToString(" · ")
