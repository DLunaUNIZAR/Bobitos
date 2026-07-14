package com.dlunaunizar.bobitos.core.navigation

import androidx.annotation.StringRes
import com.dlunaunizar.bobitos.R

enum class BobitosDestination(
    val route: String,
    @param:StringRes val titleRes: Int,
    val iconText: String,
) {
    Spaces(
        route = "spaces",
        titleRes = R.string.spaces_title,
        iconText = "E",
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
    ;

    companion object {
        val workspaceDestinations = listOf(Shopping, Tasks, Calendar)
    }
}
