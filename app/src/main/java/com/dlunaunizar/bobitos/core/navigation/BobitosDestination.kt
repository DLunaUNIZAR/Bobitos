package com.dlunaunizar.bobitos.core.navigation

import androidx.annotation.StringRes
import com.dlunaunizar.bobitos.R

enum class BobitosDestination(val route: String, @param:StringRes val titleRes: Int, val iconText: String) {
    Spaces(
        route = "spaces",
        titleRes = R.string.spaces_title,
        iconText = "E",
    ),
    MyCalendar(
        route = "my-calendar",
        titleRes = R.string.my_calendar_title,
        iconText = "📆",
    ),
    Shopping(
        route = "shopping",
        titleRes = R.string.shopping_title,
        iconText = "🛒",
    ),
    Tasks(
        route = "tasks",
        titleRes = R.string.tasks_title,
        iconText = "✓",
    ),
    Calendar(
        route = "calendar",
        titleRes = R.string.calendar_title,
        iconText = "📅",
    ),
    Profile(
        route = "profile",
        titleRes = R.string.profile_title,
        iconText = "P",
    ),
    SpaceSettings(
        route = "space-settings",
        titleRes = R.string.space_settings_title,
        iconText = "A",
    ),
    ;

    companion object {
        val rootDestinations = listOf(Spaces, MyCalendar)
        val workspaceDestinations = listOf(Shopping, Tasks, Calendar)
    }
}
