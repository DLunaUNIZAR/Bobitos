package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogExercise

data class ExercisesUiState(
    val query: String = "",
    val catalog: UiState<List<CatalogExercise>> = UiState.Loading,
    // Cuenta activa: puede curar (editar/borrar) fichas ajenas del catálogo común.
    val isAdmin: Boolean = false,
    val currentUid: String? = null,
    val isSaving: Boolean = false,
    val error: ExerciseUiMessage? = null,
    val notice: ExerciseUiMessage? = null,
) {
    fun canEdit(exercise: CatalogExercise): Boolean = isAdmin || exercise.ownerUid == currentUid
}

enum class ExerciseUiMessage {
    NameRequired,
    NameTooLong,
    MuscleGroupTooLong,
    AlreadyExists,
    NotAuthenticated,
    EmailNotVerified,
    NotFound,
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    Saved,
    Deleted,
}
