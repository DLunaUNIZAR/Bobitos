package com.dlunaunizar.bobitos.core.navigation

import com.dlunaunizar.bobitos.app.RealtimeScope

/**
 * Qué datos de espacios se escuchan en tiempo real según la pantalla. Con el selector de espacio abierto
 * hacen falta todos (dentro de un espacio solo se escucha el activo y el selector listaría uno solo).
 */
fun realtimeScopeFor(route: String?, spacePickerOpen: Boolean): RealtimeScope = when {
    spacePickerOpen -> RealtimeScope.ALL_SPACES
    route == null -> RealtimeScope.AUTOMATIC
    route in allSpacesRoutes -> RealtimeScope.ALL_SPACES
    route in spacelessRoutes -> RealtimeScope.PAUSED
    else -> RealtimeScope.ACTIVE_SPACE
}

private val allSpacesRoutes = setOf(
    BobitosDestination.Home.route,
    BobitosDestination.Spaces.route,
    BobitosDestination.MyCalendar.route,
)

private val spacelessRoutes = setOf(
    BobitosDestination.Profile.route,
    BobitosDestination.Recipes.route,
    BobitosDestination.Ingredients.route,
    BobitosDestination.Exercises.route,
    BobitosDestination.Routines.route,
    INGREDIENT_DETAIL_ROUTE,
)
