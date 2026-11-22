package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseInput
import kotlinx.coroutines.flow.Flow

/**
 * Ejercicios del usuario: el catálogo común (`exercises`, lo escribe solo el admin) y los personales
 * (`users/{uid}/exercises`, solo los ve y edita su dueño). Espejo de [IngredientRepository] +
 * [PersonalIngredientRepository], con una sola lista para la interfaz.
 */
interface ExerciseRepository {
    /** Comunes y personales mezclados y ordenados; los personales llevan `personal = true`. */
    fun catalog(): Flow<List<CatalogExercise>>

    fun isCurrentUserCatalogAdmin(): Boolean

    fun currentUserId(): String?

    /** El admin crea fichas comunes; cualquier otro usuario, personales. Devuelve el id (slug del nombre). */
    suspend fun createExercise(input: ExerciseInput): String

    suspend fun updateExercise(id: String, personal: Boolean, input: ExerciseInput)

    suspend fun deleteExercise(id: String, personal: Boolean)

    /**
     * «Deshacer» el borrado de un ejercicio propio: lo escribe con su id original y sus campos editables, en el
     * catálogo común o en los personales según `exercise.personal`. El dueño pasa a ser quien deshace (las reglas
     * de alta lo exigen) y no lleva `source` ni `image`.
     */
    suspend fun restoreExercise(exercise: CatalogExercise)
}

enum class ExerciseFailure {
    NameRequired,
    NameTooLong,
    MuscleGroupTooLong,
    DescriptionTooLong,
    NotAuthenticated,
    EmailNotVerified,
    ExerciseNotFound,
    PermissionDenied,
    AlreadyExists,
    Network,
    Unknown,
}

class ExerciseRepositoryException(val failure: ExerciseFailure, cause: Throwable? = null) : Exception(cause)
