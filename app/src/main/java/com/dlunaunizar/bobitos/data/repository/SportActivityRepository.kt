package com.dlunaunizar.bobitos.data.repository

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
    )

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

    suspend fun deleteActivity(spaceId: String, activityId: String)
}

enum class SportFailure {
    NameRequired,
    NameTooLong,
    InvalidParticipants,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    ActivityNotFound,
    PermissionDenied,
    Network,
    Unknown,
}

class SportRepositoryException(val failure: SportFailure, cause: Throwable? = null) : Exception(cause)
