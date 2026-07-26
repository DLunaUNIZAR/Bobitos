package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import kotlinx.coroutines.flow.Flow

/**
 * Catálogo de rutinas de entrenamiento (≈ [RecipeRepository]). Las GLOBAL las publica un admin; cada
 * usuario crea las suyas PRIVATE. Embebe la lista de ejercicios con sus parámetros por tipo.
 */
interface RoutineRepository {
    fun globalRoutines(): Flow<List<Routine>>
    fun myRoutines(): Flow<List<Routine>>

    fun isCurrentUserRoutineAdmin(): Boolean

    suspend fun createRoutine(
        visibility: RoutineVisibility,
        title: String,
        description: String?,
        exercises: List<RoutineExercise>,
    )

    suspend fun updateRoutine(routineId: String, title: String, description: String?, exercises: List<RoutineExercise>)

    suspend fun deleteRoutine(routineId: String)
}

enum class RoutineFailure {
    TitleRequired,
    TitleTooLong,
    DescriptionTooLong,
    NotAuthenticated,
    EmailNotVerified,
    RoutineNotFound,
    PermissionDenied,
    Network,
    Unknown,
}

class RoutineRepositoryException(val failure: RoutineFailure, cause: Throwable? = null) : Exception(cause)
