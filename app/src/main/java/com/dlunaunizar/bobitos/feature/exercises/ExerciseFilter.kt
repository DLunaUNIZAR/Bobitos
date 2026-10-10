package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.core.common.foldForSearch
import com.dlunaunizar.bobitos.core.common.prepareQuery
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.MAX_EXERCISE_NAME_LENGTH
import java.text.Collator
import java.util.Locale

// Filtro del catálogo de ejercicios, el mismo en la pantalla Ejercicios y en el selector.
internal data class ExerciseFilter(
    val query: String = "",
    val type: ExerciseType? = null,
    val muscleGroup: String? = null,
) {
    val isActive: Boolean get() = query.isNotBlank() || type != null || muscleGroup != null
}

// Conserva el orden del catálogo. La búsqueda mira nombre y grupo; tipo y grupo se combinan con ella.
// El grupo se compara plegado (sin tildes ni mayúsculas).
internal fun List<CatalogExercise>.filterExercises(filter: ExerciseFilter): List<CatalogExercise> {
    val prepared = prepareQuery(filter.query)
    val group = filter.muscleGroup?.foldForSearch()
    return filter {
        (filter.type == null || it.type == filter.type) &&
            (group == null || it.muscleGroup?.foldForSearch() == group) &&
            prepared.matches(it.name, it.muscleGroup)
    }
}

// Grupos musculares presentes, sin repetidos (plegados), en orden alfabético del español. Se muestra la
// forma original de la primera aparición.
internal fun List<CatalogExercise>.muscleGroups(): List<String> {
    val collator = Collator.getInstance(Locale.forLanguageTag("es-ES"))
    return mapNotNull { it.muscleGroup?.trim()?.takeIf(String::isNotEmpty) }
        .distinctBy { it.foldForSearch() }
        .sortedWith(collator)
}

private val WHITESPACE = Regex("[\\s\\u00A0\\u2007\\u202F]+")

// Nombre del ejercicio personalizado a partir de lo buscado: recortado, con espacios unidos y sin pasar del límite.
internal fun customExerciseName(query: String): String =
    query.trim().replace(WHITESPACE, " ").take(MAX_EXERCISE_NAME_LENGTH).trimEnd()
