package com.dlunaunizar.bobitos.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
import com.dlunaunizar.bobitos.core.designsystem.theme.moduleColors

enum class BobitosDestination(val route: String, @param:StringRes val titleRes: Int, val icon: ImageVector) {
    Home(
        route = "home",
        titleRes = R.string.app_name,
        icon = Icons.Rounded.Dashboard,
    ),
    Spaces(
        route = "spaces",
        titleRes = R.string.spaces_title,
        icon = Icons.Rounded.Groups,
    ),
    SpaceHome(
        route = "space-home",
        titleRes = R.string.space_today_title,
        icon = Icons.Rounded.Dashboard,
    ),
    SpaceMore(
        route = "space-more",
        titleRes = R.string.space_more_title,
        icon = Icons.Rounded.MoreHoriz,
    ),
    MyCalendar(
        route = "my-calendar",
        titleRes = R.string.my_calendar_title,
        icon = Icons.Rounded.CalendarMonth,
    ),
    Shopping(
        route = "shopping",
        titleRes = R.string.shopping_title,
        icon = Icons.Rounded.ShoppingCart,
    ),
    Tasks(
        route = "tasks",
        titleRes = R.string.tasks_title,
        icon = Icons.Rounded.Checklist,
    ),
    Calendar(
        route = "calendar",
        titleRes = R.string.calendar_title,
        icon = Icons.Rounded.Event,
    ),
    Meals(
        route = "meals",
        titleRes = R.string.meals_title,
        icon = Icons.Rounded.Restaurant,
    ),
    Sport(
        route = "sport",
        titleRes = R.string.sport_title,
        icon = Icons.Rounded.FitnessCenter,
    ),
    Recipes(
        route = "recipes",
        titleRes = R.string.recipes_title,
        icon = Icons.Rounded.MenuBook,
    ),
    Ingredients(
        route = "ingredients",
        titleRes = R.string.ingredients_title,
        icon = Icons.Rounded.Kitchen,
    ),
    Exercises(
        route = "exercises",
        titleRes = R.string.exercises_title,
        icon = Icons.Rounded.FitnessCenter,
    ),
    Routines(
        route = "routines",
        titleRes = R.string.routines_title,
        icon = Icons.AutoMirrored.Rounded.ListAlt,
    ),
    Notes(
        route = "notes",
        titleRes = R.string.notes_title,
        icon = Icons.AutoMirrored.Rounded.StickyNote2,
    ),
    Profile(
        route = "profile",
        titleRes = R.string.profile_title,
        icon = Icons.Rounded.Person,
    ),
    SpaceSettings(
        route = "space-settings",
        titleRes = R.string.space_settings_title,
        icon = Icons.Rounded.Settings,
    ),
    ;

    companion object {
        // Tarjetas del menú principal (al abrir la app): espacios + catálogos globales + calendario personal.
        val mainMenuDestinations = listOf(Spaces, Recipes, Ingredients, Routines, Exercises, MyCalendar)
        val workspaceDestinations = listOf(Shopping, Tasks, Calendar, Meals, Sport)

        // Pestañas de la barra inferior dentro de un espacio. Comidas y Deporte cuelgan de «Más».
        val workspaceTabs = listOf(SpaceHome, Calendar, Tasks, Shopping, SpaceMore)
    }
}

// Color de módulo dentro de un espacio, para dar identidad de color al hub y a la barra
// inferior. Solo los cinco módulos del workspace tienen color; el resto, null (neutro).
@Composable
fun BobitosDestination.moduleColor(): Color? = when (this) {
    BobitosDestination.Shopping -> moduleColors(AppModule.SHOPPING).accent
    BobitosDestination.Tasks -> moduleColors(AppModule.TASKS).accent
    BobitosDestination.Calendar -> moduleColors(AppModule.CALENDAR).accent
    BobitosDestination.Meals -> moduleColors(AppModule.MEALS).accent
    BobitosDestination.Sport -> moduleColors(AppModule.SPORT).accent
    else -> null
}

internal const val CALENDAR_EVENT_ROUTE = "calendar-event/{eventId}/{date}"
internal const val INGREDIENT_DETAIL_ROUTE = "ingredient-detail/{ingredientId}"

/** Pestaña que se resalta para la ruta actual, o null si la ruta no es una pantalla del espacio. */
fun workspaceTabFor(route: String?): BobitosDestination? = when (route) {
    BobitosDestination.SpaceHome.route -> BobitosDestination.SpaceHome
    BobitosDestination.Calendar.route, CALENDAR_EVENT_ROUTE -> BobitosDestination.Calendar
    BobitosDestination.Tasks.route -> BobitosDestination.Tasks
    BobitosDestination.Shopping.route -> BobitosDestination.Shopping
    BobitosDestination.SpaceMore.route,
    BobitosDestination.Meals.route,
    BobitosDestination.Sport.route,
    -> BobitosDestination.SpaceMore
    else -> null
}
