package com.dlunaunizar.bobitos.feature.meals

import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.MealSlot

// Coincidencia por texto (nombre de la comida) para el buscador; en blanco no filtra.
internal fun Meal.matchesQuery(query: String): Boolean =
    query.isBlank() || name.contains(query.trim(), ignoreCase = true)

/**
 * Las franjas que pinta el día, con sus comidas. Sin búsqueda salen las tres, también las vacías (con su «Añadir»);
 * al buscar, solo las que tienen coincidencias. Con el día sin comidas la búsqueda no se aplica: el buscador no se ve
 * y no se podría borrar.
 */
internal fun visibleMealSlots(dayMeals: List<Meal>, query: String): List<Pair<MealSlot, List<Meal>>> {
    val searching = query.isNotBlank() && dayMeals.isNotEmpty()
    val slots = MealSlot.entries.map { slot -> slot to dayMeals.filter { it.slot == slot && it.matchesQuery(query) } }
    return if (searching) slots.filter { it.second.isNotEmpty() } else slots
}
