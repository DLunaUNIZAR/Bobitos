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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.app.AppUiState
import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.feature.auth.AuthActionUiState
import com.dlunaunizar.bobitos.feature.auth.ProfileScreen
import com.dlunaunizar.bobitos.feature.calendar.CalendarScreen
import com.dlunaunizar.bobitos.feature.shopping.ShoppingScreen
import com.dlunaunizar.bobitos.feature.spaces.SpacesScreen
import com.dlunaunizar.bobitos.feature.spaces.SpaceManagementUiState
import com.dlunaunizar.bobitos.feature.spaces.SpaceSettingsScreen
import com.dlunaunizar.bobitos.feature.tasks.TasksScreen

@Composable
fun BobitosNavHost(
    navController: NavHostController,
    uiState: AppUiState,
    authUser: AuthUser,
    authActionState: AuthActionUiState,
    spaceManagementState: SpaceManagementUiState,
    onSpaceSelected: (String) -> Unit,
    onCreateSpace: (String) -> Unit,
    onObserveMembers: (String) -> Unit,
    onStopObservingMembers: () -> Unit,
    onRenameSpace: (String, String) -> Unit,
    onLeaveSpace: (String) -> Unit,
    onRemoveMember: (String, String) -> Unit,
    onTransferOwnership: (String, String) -> Unit,
    onClearSpaceFeedback: () -> Unit,
    onUpdateDisplayName: (String) -> Unit,
    onSignOut: () -> Unit,
    onClearAuthFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spaceName = uiState.selectedSpace?.name ?: stringResource(R.string.app_name)
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val protectedRoutes = BobitosDestination.workspaceDestinations.map { it.route } +
        BobitosDestination.SpaceSettings.route

    LaunchedEffect(uiState.selectedSpace, currentRoute) {
        if (uiState.selectedSpace == null && currentRoute in protectedRoutes) {
            navController.navigateToSpaces()
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (uiState.selectedSpace == null) {
            BobitosDestination.Spaces.route
        } else {
            BobitosDestination.Shopping.route
        },
        modifier = modifier.fillMaxSize(),
    ) {
        composable(BobitosDestination.Spaces.route) {
            SpacesScreen(
                state = uiState.spaces,
                managementState = spaceManagementState,
                onProfileClick = {
                    onClearAuthFeedback()
                    navController.navigateToProfile()
                },
                onSpaceSelected = { space ->
                    onClearSpaceFeedback()
                    onSpaceSelected(space.id)
                    navController.navigate(BobitosDestination.Shopping.route) {
                        popUpTo(BobitosDestination.Spaces.route) {
                            inclusive = true
                        }
                    }
                },
                onCreateSpace = onCreateSpace,
                onClearFeedback = onClearSpaceFeedback,
            )
        }

        composable(BobitosDestination.Shopping.route) {
            WorkspaceScaffold(
                currentDestination = BobitosDestination.Shopping,
                spaceName = spaceName,
                onDestinationSelected = navController::navigateToWorkspace,
                onSwitchSpace = navController::navigateToSpaces,
                onSpaceSettings = {
                    onClearSpaceFeedback()
                    navController.navigate(BobitosDestination.SpaceSettings.route)
                },
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
                onSpaceSettings = {
                    onClearSpaceFeedback()
                    navController.navigate(BobitosDestination.SpaceSettings.route)
                },
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
                onSpaceSettings = {
                    onClearSpaceFeedback()
                    navController.navigate(BobitosDestination.SpaceSettings.route)
                },
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

        composable(BobitosDestination.SpaceSettings.route) {
            uiState.selectedSpace?.let { space ->
                SpaceSettingsScreen(
                    space = space,
                    currentUserId = authUser.id,
                    state = spaceManagementState,
                    onObserveMembers = onObserveMembers,
                    onStopObservingMembers = onStopObservingMembers,
                    onRenameSpace = onRenameSpace,
                    onLeaveSpace = onLeaveSpace,
                    onRemoveMember = onRemoveMember,
                    onTransferOwnership = onTransferOwnership,
                    onClearFeedback = onClearSpaceFeedback,
                    onBack = { navController.popBackStack() },
                )
            }
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
    onSpaceSettings: () -> Unit,
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
                    TextButton(onClick = onSpaceSettings) {
                        Text(text = stringResource(R.string.space_settings))
                    }
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
