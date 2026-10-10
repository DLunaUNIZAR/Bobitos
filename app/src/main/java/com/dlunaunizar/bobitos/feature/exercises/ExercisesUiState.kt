package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogExercise

data class ExercisesUiState(
    val catalog: UiState<List<CatalogExercise>> = UiState.Loading,
    // Cuenta activa: puede curar (editar/borrar) fichas ajenas del catálogo común.
    val isAdmin: Boolean = false,
    val currentUid: String? = null,
    val isSaving: Boolean = false,
    val editorSave: EditorSaveStatus = EditorSaveStatus.IDLE,
    // Motivo, localizable, de que el catálogo no cargara (con `catalog` en Error).
    val catalogError: ExerciseUiMessage? = null,
    val error: ExerciseUiMessage? = null,
    val notice: ExerciseUiMessage? = null,
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
