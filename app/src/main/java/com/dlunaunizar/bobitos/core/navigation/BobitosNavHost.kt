package com.dlunaunizar.bobitos.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.app.AppUiState
import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.feature.auth.AuthActionUiState
import com.dlunaunizar.bobitos.feature.auth.ProfileScreen
import com.dlunaunizar.bobitos.feature.calendar.CalendarScreen
import com.dlunaunizar.bobitos.feature.shopping.ShoppingScreen
import com.dlunaunizar.bobitos.feature.spaces.SpacesScreen
import com.dlunaunizar.bobitos.feature.tasks.TasksScreen

@Composable
fun BobitosNavHost(
    navController: NavHostController,
    uiState: AppUiState,
    authUser: AuthUser,
    authActionState: AuthActionUiState,
    onSpaceSelected: (String) -> Unit,
    onUpdateDisplayName: (String) -> Unit,
    onSignOut: () -> Unit,
    onClearAuthFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spaceName = uiState.selectedSpace?.name ?: stringResource(R.string.app_name)

    NavHost(
        navController = navController,
        startDestination = BobitosDestination.Spaces.route,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(BobitosDestination.Spaces.route) {
            SpacesScreen(
                state = uiState.spaces,
                onProfileClick = {
                    onClearAuthFeedback()
                    navController.navigateToProfile()
                },
                onSpaceSelected = { space ->
                    onSpaceSelected(space.id)
                    navController.navigate(BobitosDestination.Shopping.route) {
                        popUpTo(BobitosDestination.Spaces.route) {
                            inclusive = true
                        }
                    }
                },
            )
        }

        composable(BobitosDestination.Shopping.route) {
            WorkspaceScaffold(
                currentDestination = BobitosDestination.Shopping,
                spaceName = spaceName,
                onDestinationSelected = navController::navigateToWorkspace,
                onSwitchSpace = navController::navigateToSpaces,
                onProfile = {
                    onClearAuthFeedback()
                    navController.navigateToProfile()
                },
            ) {
                ShoppingScreen()
            }
        }

        composable(BobitosDestination.Tasks.route) {
            WorkspaceScaffold(
                currentDestination = BobitosDestination.Tasks,
                spaceName = spaceName,
                onDestinationSelected = navController::navigateToWorkspace,
                onSwitchSpace = navController::navigateToSpaces,
                onProfile = {
                    onClearAuthFeedback()
                    navController.navigateToProfile()
                },
            ) {
                TasksScreen()
            }
        }

        composable(BobitosDestination.Calendar.route) {
            WorkspaceScaffold(
                currentDestination = BobitosDestination.Calendar,
                spaceName = spaceName,
                onDestinationSelected = navController::navigateToWorkspace,
                onSwitchSpace = navController::navigateToSpaces,
                onProfile = {
                    onClearAuthFeedback()
                    navController.navigateToProfile()
                },
            ) {
                CalendarScreen()
            }
        }

        composable(BobitosDestination.Profile.route) {
            ProfileScreen(
                user = authUser,
                actionState = authActionState,
                onUpdateDisplayName = onUpdateDisplayName,
                onSignOut = onSignOut,
                onBack = { navController.popBackStack() },
                onClearFeedback = onClearAuthFeedback,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceScaffold(
    currentDestination: BobitosDestination,
    spaceName: String,
    onDestinationSelected: (BobitosDestination) -> Unit,
    onSwitchSpace: () -> Unit,
    onProfile: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = spaceName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(text = stringResource(currentDestination.titleRes))
                    }
                },
                actions = {
                    TextButton(onClick = onProfile) {
                        Text(text = stringResource(R.string.profile_open))
                    }
                    TextButton(onClick = onSwitchSpace) {
                        Text(text = stringResource(R.string.change_space))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                BobitosDestination.workspaceDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == currentDestination,
                        onClick = { onDestinationSelected(destination) },
                        icon = { Text(text = destination.iconText) },
                        label = { Text(text = stringResource(destination.titleRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            content()
        }
    }
}

private fun NavHostController.navigateToWorkspace(destination: BobitosDestination) {
    navigate(destination.route) {
        popUpTo(BobitosDestination.Shopping.route) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateToSpaces() {
    navigate(BobitosDestination.Spaces.route) {
        popUpTo(BobitosDestination.Shopping.route) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateToProfile() {
    navigate(BobitosDestination.Profile.route) {
        launchSingleTop = true
    }
}
