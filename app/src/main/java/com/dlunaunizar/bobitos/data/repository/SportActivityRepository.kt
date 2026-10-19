package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.SportActivity
import com.dlunaunizar.bobitos.core.model.SportType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface SportActivityRepository {
    fun activities(spaceId: String, weekStart: LocalDate, weekEndExclusive: LocalDate): Flow<List<SportActivity>>

    suspend fun addActivity(
        spaceId: String,
        date: LocalDate,
        type: SportType,
        name: String,
        participantIds: List<String>,
        routineId: String? = null,
        session: List<RoutineExercise> = emptyList(),
    ): String

    suspend fun updateActivity(
        spaceId: String,
        activityId: String,
        date: LocalDate,
        type: SportType,
        name: String,
        participantIds: List<String>,
        routineId: String? = null,
        session: List<RoutineExercise> = emptyList(),
    )

    suspend fun setDone(spaceId: String, activityId: String, done: Boolean)

    // Borra la actividad y su evento enlazado. Devuelve la foto del evento que ha borrado (con los datos que tenía,
    // también si se editó desde el Calendario), o null si no tenía o ya no existía.
    suspend fun deleteActivity(spaceId: String, activityId: String): CalendarEvent?

    // «Deshacer» el borrado: la actividad vuelve con su id y su estado (hecha) y, con ella, su evento con su id y sus
    // datos, todo en una sola transacción. Los participantes que ya no son miembros activos se quitan, de la actividad
    // y del evento. La actividad solo vuelve enlazada a un evento que vuelve con ella. La autoría y las fechas de
    // creación pasan a ser de quien deshace, como exigen las reglas de alta.
    suspend fun restoreActivity(spaceId: String, activity: SportActivity, event: CalendarEvent?)

    // «Deshacer» un alta: borra la actividad y su evento enlazado solo si nadie más los ha tocado desde entonces
    // (si no, ActivityChanged).
    suspend fun undoAddActivity(spaceId: String, activityId: String)
}

enum class SportFailure {
    NameRequired,
    NameTooLong,
    InvalidParticipants,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    ActivityNotFound,
    ActivityChanged,
    PermissionDenied,
    Network,
    Unknown,
}

class SportRepositoryException(val failure: SportFailure, cause: Throwable? = null) : Exception(cause)
