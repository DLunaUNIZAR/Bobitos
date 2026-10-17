package com.dlunaunizar.bobitos.feature.exercises

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.LocalDarkTheme
import com.dlunaunizar.bobitos.core.designsystem.theme.themedColor
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
