package com.dlunaunizar.bobitos.feature.sport

import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SportActivity
import java.time.LocalDate
import java.time.temporal.ChronoField

data class SportUiState(
    val focusedDate: LocalDate = LocalDate.now(),
    val activities: UiState<List<SportActivity>> = UiState.Loading,
    val members: UiState<List<SpaceMember>> = UiState.Loading,
    val isSaving: Boolean = false,
    val error: SportUiMessage? = null,
    val notice: SportUiMessage? = null,
) {
    // Lunes de la semana del día enfocado (ISO: día 1 = lunes). Define la ventana observada.
    val weekStart: LocalDate get() = focusedDate.with(ChronoField.DAY_OF_WEEK, 1L)

    // Los 7 días (lunes→domingo) de la semana enfocada, para la tira selectora.
    val weekDays: List<LocalDate> get() = (0L until DAYS_IN_WEEK).map(weekStart::plusDays)

    private companion object {
        const val DAYS_IN_WEEK = 7L
    }
}

// Mensajes de UI del módulo Deporte (mismo patrón que MealUiMessage). La pantalla los mapea a strings.
enum class SportUiMessage {
    NameRequired,
    NameTooLong,
    InvalidParticipants,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    ActivityNotFound,
    PermissionDenied,
    NetworkError,
    UnexpectedError,
    ActivityAdded,
    ActivityUpdated,
    ActivityDeleted,
    ActivityDone,
    ActivityPending,
}
