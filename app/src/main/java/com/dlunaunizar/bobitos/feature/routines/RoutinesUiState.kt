package com.dlunaunizar.bobitos.feature.routines

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.Routine

data class RoutinesUiState(
    val query: String = "",
    val global: UiState<List<Routine>> = UiState.Loading,
    val mine: UiState<List<Routine>> = UiState.Loading,
    // Catálogo de ejercicios para el selector del editor.
    val exercises: List<CatalogExercise> = emptyList(),
    val isAdmin: Boolean = false,
    val isSaving: Boolean = false,
    val error: RoutineUiMessage? = null,
    val notice: RoutineUiMessage? = null,
)

enum class RoutineUiMessage {
    TitleRequired,
    TitleTooLong,
    DescriptionTooLong,
    NotAuthenticated,
    EmailNotVerified,
    NotFound,
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    Saved,
    Deleted,
}

object RoutinesValidation {
    const val MAX_TITLE = 120
    const val MAX_DESCRIPTION = 1000

    fun validate(title: String, description: String?): RoutineUiMessage? = when {
        title.isBlank() -> RoutineUiMessage.TitleRequired
        title.trim().length > MAX_TITLE -> RoutineUiMessage.TitleTooLong
        description?.trim()?.length?.let { it > MAX_DESCRIPTION } == true -> RoutineUiMessage.DescriptionTooLong
        else -> null
    }
}
