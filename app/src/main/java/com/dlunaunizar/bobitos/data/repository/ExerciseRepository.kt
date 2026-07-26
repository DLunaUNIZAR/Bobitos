package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseType
import kotlinx.coroutines.flow.Flow

/**
 * Catálogo global de ejercicios compartido por todos los usuarios. Lo crea cualquier usuario
 * verificado; solo su autor (o un admin) puede editarlo/borrarlo. Espejo de [IngredientRepository].
 */
interface ExerciseRepository {
    fun catalog(): Flow<List<CatalogExercise>>

    fun isCurrentUserCatalogAdmin(): Boolean

    fun currentUserId(): String?

    suspend fun exerciseById(id: String): CatalogExercise?

    suspend fun createExercise(name: String, type: ExerciseType, muscleGroup: String?)

    suspend fun updateExercise(id: String, name: String, type: ExerciseType, muscleGroup: String?)

    suspend fun deleteExercise(id: String)
}

enum class ExerciseFailure {
    NameRequired,
    NameTooLong,
    MuscleGroupTooLong,
    NotAuthenticated,
    EmailNotVerified,
    ExerciseNotFound,
    PermissionDenied,
    Network,
    Unknown,
}

class ExerciseRepositoryException(val failure: ExerciseFailure, cause: Throwable? = null) : Exception(cause)
