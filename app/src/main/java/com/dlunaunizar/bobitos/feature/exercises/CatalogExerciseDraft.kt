package com.dlunaunizar.bobitos.feature.exercises

import android.os.Parcelable
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseEquipment
import com.dlunaunizar.bobitos.core.model.ExerciseInput
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.MAX_EXERCISE_DESCRIPTION_LENGTH
import com.dlunaunizar.bobitos.core.model.MAX_EXERCISE_MUSCLE_LENGTH
import com.dlunaunizar.bobitos.core.model.MAX_EXERCISE_NAME_LENGTH
import com.dlunaunizar.bobitos.core.model.SetMeasure
import com.dlunaunizar.bobitos.core.model.slug
import kotlinx.parcelize.Parcelize

// Fallos de un borrador, los mismos que el repositorio rechazaría (así el editor no pierde lo escrito).
internal enum class ExerciseDraftError { NameRequired, NameTooLong, MuscleTooLong, DescriptionTooLong, NameExists }

// Errores que ve el editor. «Ya existe» se oculta mientras guarda y al terminar (SAVED): el catálogo ya
// trae la ficha recién creada antes de que se cierre la hoja y no es un error del usuario.
internal fun visibleDraftErrors(
    errors: Set<ExerciseDraftError>,
    saving: Boolean,
    saved: Boolean,
): Set<ExerciseDraftError> = if (saving || saved) errors - ExerciseDraftError.NameExists else errors

// Borrador del editor de ejercicio del catálogo. Parcelable para que sobreviva a una rotación.
@Parcelize
internal data class CatalogExerciseDraft(
    val name: String,
    val type: ExerciseType,
    val muscle: String,
    val description: String = "",
    val equipment: List<ExerciseEquipment> = emptyList(),
    val measure: SetMeasure = SetMeasure.REPS,
) : Parcelable {
    fun toInput() = ExerciseInput(
        name = name.trim(),
        type = type,
        muscleGroup = muscle.trim().ifBlank { null },
        description = description.trim().ifBlank { null },
        equipment = equipment,
        measure = if (type.isStrength) measure else SetMeasure.REPS,
    )

    // otherIds: ids (slugs) de los demás ejercicios del catálogo, sin el que se edita.
    fun errors(otherIds: Set<String>): Set<ExerciseDraftError> = buildSet {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) add(ExerciseDraftError.NameRequired)
        if (trimmedName.length > MAX_EXERCISE_NAME_LENGTH) add(ExerciseDraftError.NameTooLong)
        if (muscle.trim().length > MAX_EXERCISE_MUSCLE_LENGTH) add(ExerciseDraftError.MuscleTooLong)
        if (description.trim().length > MAX_EXERCISE_DESCRIPTION_LENGTH) add(ExerciseDraftError.DescriptionTooLong)
        if (trimmedName.isNotEmpty() && slug(trimmedName) in otherIds) add(ExerciseDraftError.NameExists)
    }

    companion object {
        fun of(exercise: CatalogExercise?) = CatalogExerciseDraft(
            name = exercise?.name.orEmpty(),
            type = exercise?.type ?: ExerciseType.MAQUINA,
            muscle = exercise?.muscleGroup.orEmpty(),
            description = exercise?.description.orEmpty(),
            equipment = exercise?.equipment.orEmpty(),
            measure = exercise?.measure ?: SetMeasure.REPS,
        )
    }
}
