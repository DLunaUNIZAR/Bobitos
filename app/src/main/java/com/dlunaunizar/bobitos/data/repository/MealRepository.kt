package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface MealRepository {
    fun meals(spaceId: String, weekStart: LocalDate, weekEndExclusive: LocalDate): Flow<List<Meal>>

    suspend fun addMeal(
        spaceId: String,
        date: LocalDate,
        slot: MealSlot,
        name: String,
        participantIds: List<String>,
        recipeId: String?,
    )

    suspend fun updateMeal(
        spaceId: String,
        mealId: String,
        date: LocalDate,
        slot: MealSlot,
        name: String,
        participantIds: List<String>,
        recipeId: String?,
    )

    suspend fun deleteMeal(spaceId: String, mealId: String)
}

enum class MealFailure {
    NameRequired,
    NameTooLong,
    InvalidParticipants,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    MealNotFound,
    PermissionDenied,
    Network,
    Unknown,
}

class MealRepositoryException(val failure: MealFailure, cause: Throwable? = null) : Exception(cause)
