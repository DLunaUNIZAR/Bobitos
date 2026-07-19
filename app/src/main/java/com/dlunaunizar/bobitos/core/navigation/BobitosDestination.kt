package com.dlunaunizar.bobitos.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dlunaunizar.bobitos.R

enum class BobitosDestination(val route: String, @param:StringRes val titleRes: Int, val icon: ImageVector) {
    Spaces(
        route = "spaces",
        titleRes = R.string.spaces_title,
        icon = Icons.Rounded.Groups,
    ),
    SpaceHome(
        route = "space-home",
        titleRes = R.string.space_home_title,
        icon = Icons.Rounded.Dashboard,
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
        val rootDestinations = listOf(Spaces, MyCalendar)
        val workspaceDestinations = listOf(Shopping, Tasks, Calendar)
    }
}

// Color de módulo dentro de un espacio, para dar identidad de color al hub y a la barra
// inferior. Solo los tres módulos del workspace tienen color; el resto, null (neutro).
fun BobitosDestination.moduleColor(): Color? = when (this) {
    BobitosDestination.Shopping -> Color(0xFFC05621)
    BobitosDestination.Tasks -> Color(0xFF7E57C2)
    BobitosDestination.Calendar -> Color(0xFF00897B)
    else -> null
}
