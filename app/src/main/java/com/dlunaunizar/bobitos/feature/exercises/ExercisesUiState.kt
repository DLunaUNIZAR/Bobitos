package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.core.common.SaveState
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogExercise

data class ExercisesUiState(
    val catalog: UiState<List<CatalogExercise>> = UiState.Loading,
    // Cuenta activa: puede curar (editar/borrar) fichas ajenas del catálogo común.
    val isAdmin: Boolean = false,
    val currentUid: String? = null,
    // Motivo, localizable, de que el catálogo no cargara (con `catalog` en Error).
    val catalogError: ExerciseUiMessage? = null,
    val save: SaveState<ExerciseUiMessage, ExerciseUiMessage> = SaveState(),
) {
    fun canEdit(exercise: CatalogExercise): Boolean = isAdmin || exercise.ownerUid == currentUid
}

enum class ExerciseUiMessage {
    NameRequired,
    NameTooLong,
    MuscleGroupTooLong,
    DescriptionTooLong,
    AlreadyExists,
    NotAuthenticated,
    EmailNotVerified,
    NotFound,
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    SaveTimeout,
    CatalogUnavailable,
    Saved,
    Deleted,
}
