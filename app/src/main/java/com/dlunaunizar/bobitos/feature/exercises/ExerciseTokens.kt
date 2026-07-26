package com.dlunaunizar.bobitos.feature.exercises

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.model.ExerciseType

@get:StringRes
internal val ExerciseType.labelRes: Int
    get() = when (this) {
        ExerciseType.MAQUINA -> R.string.exercise_type_machine
        ExerciseType.PESO_LIBRE -> R.string.exercise_type_free_weight
        ExerciseType.CARDIO -> R.string.exercise_type_cardio
        ExerciseType.OTROS -> R.string.exercise_type_other
    }

// Cierto si el tipo registra series con peso (máquina/peso libre) frente a tiempo/nivel (cardio).
internal val ExerciseType.isStrength: Boolean
    get() = this == ExerciseType.MAQUINA || this == ExerciseType.PESO_LIBRE

internal fun ExerciseType.accent(): Color = when (this) {
    ExerciseType.MAQUINA -> Color(0xFF1565C0)
    ExerciseType.PESO_LIBRE -> Color(0xFF6A1B9A)
    ExerciseType.CARDIO -> Color(0xFFC05621)
    ExerciseType.OTROS -> Color(0xFF6E6E6E)
}
