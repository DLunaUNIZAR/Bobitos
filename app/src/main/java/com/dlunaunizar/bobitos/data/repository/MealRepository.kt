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
        cookId: String?,
    ): String

    suspend fun updateMeal(
        spaceId: String,
        mealId: String,
        date: LocalDate,
        slot: MealSlot,
        name: String,
        participantIds: List<String>,
        recipeId: String?,
        cookId: String?,
    )

    suspend fun setCooked(spaceId: String, mealId: String, cooked: Boolean)

    suspend fun deleteMeal(spaceId: String, mealId: String)

    /**
     * «Deshacer» un alta: borra [mealId] solo si quien deshace fue el último en escribirla. Si no existe, falla con
     * [MealFailure.MealNotFound]; si la ha cambiado otro miembro, con [MealFailure.MealChanged].
     */
    suspend fun undoAddMeal(spaceId: String, mealId: String)

    // «Deshacer» el borrado: la comida vuelve con su id y su estado (cocinada, receta, cocinero, participantes), sin
    // los participantes ni el cocinero que ya no son miembros activos. La autoría y las fechas de creación pasan a
    // ser de quien deshace, como exigen las reglas de alta.
    suspend fun restoreMeal(spaceId: String, meal: Meal)
}

enum class MealFailure {
    NameRequired,
    NameTooLong,
    InvalidParticipants,
    NotAuthenticated,
    EmailNotVerified,
    SpaceNotFound,
    MealNotFound,
    MealChanged,
    PermissionDenied,
    Network,
    Unknown,
}

class MealRepositoryException(val failure: MealFailure, cause: Throwable? = null) : Exception(cause)
