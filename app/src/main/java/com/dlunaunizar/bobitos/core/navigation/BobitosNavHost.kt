package com.dlunaunizar.bobitos.core.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.app.AppUiState
import com.dlunaunizar.bobitos.app.RealtimeScope
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SyncStatus
import com.dlunaunizar.bobitos.core.model.canWrite
import com.dlunaunizar.bobitos.feature.auth.AuthActionUiState
import com.dlunaunizar.bobitos.feature.auth.ProfileScreen
import com.dlunaunizar.bobitos.feature.calendar.CalendarScreen
import com.dlunaunizar.bobitos.feature.calendar.PersonalCalendarScreen
import com.dlunaunizar.bobitos.feature.common.SyncStatusBanner
import com.dlunaunizar.bobitos.feature.shopping.ShoppingScreen
import com.dlunaunizar.bobitos.feature.spaces.SpaceManagementUiState
import com.dlunaunizar.bobitos.feature.spaces.SpaceSettingsScreen
import com.dlunaunizar.bobitos.feature.spaces.SpacesScreen
import com.dlunaunizar.bobitos.feature.tasks.TasksScreen
import java.time.LocalDate

@Composable
fun BobitosNavHost(
    navController: NavHostController,
    uiState: AppUiState,
    authUser: AuthUser,
    authActionState: AuthActionUiState,
    spaceManagementState: SpaceManagementUiState,
    onSpaceSelected: (String) -> Unit,
    onRealtimeScopeChanged: (RealtimeScope) -> Unit,
    onCreateSpace: (String) -> Unit,
    onObserveSpaceSettings: (String, Boolean) -> Unit,
    onStopObservingSpaceSettings: () -> Unit,
    onRenameSpace: (String, String) -> Unit,
    onLeaveSpace: (String) -> Unit,
    onRemoveMember: (String, String) -> Unit,
    onTransferOwnership: (String, String) -> Unit,
    onDeleteSpace: (String) -> Unit,
    onCreateInvitation: (String) -> Unit,
    onRevokeInvitation: (String) -> Unit,
    onAcceptInvitation: (String) -> Unit,
    onShareInvitation: (SpaceInvitation) -> Unit,
    onConsumeAcceptedSpace: () -> Unit,
    pendingInvitationCode: String?,
    onInvitationCodeConsumed: () -> Unit,
    onClearSpaceFeedback: () -> Unit,
    onUpdateDisplayName: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: (String) -> Unit,
    onClearAuthFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spaceName = uiState.selectedSpace?.name ?: stringResource(R.string.app_name)
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val protectedRoutes = BobitosDestination.workspaceDestinations.map { it.route } +
        BobitosDestination.SpaceSettings.route + CALENDAR_EVENT_ROUTE

    LaunchedEffect(currentRoute) {
        onRealtimeScopeChanged(
            when (currentRoute) {
                null -> RealtimeScope.AUTOMATIC
                BobitosDestination.Spaces.route,
                BobitosDestination.MyCalendar.route,
                -> RealtimeScope.ALL_SPACES
                BobitosDestination.Profile.route -> RealtimeScope.PAUSED
                else -> RealtimeScope.ACTIVE_SPACE
            },
        )
    }

    LaunchedEffect(uiState.selectedSpace, currentRoute) {
        if (
            uiState.spaces is UiState.Content &&
            uiState.selectedSpace == null &&
            currentRoute in protectedRoutes
        ) {
            navController.navigateToSpaces()
        }
    }

    LaunchedEffect(pendingInvitationCode, currentRoute) {
        if (
            pendingInvitationCode != null &&
            currentRoute != null &&
            currentRoute != BobitosDestination.Spaces.route
        ) {
            navController.navigateToSpaces()
        }
    }

    val acceptedSpaceId = spaceManagementState.acceptedSpaceId
    val acceptedSpaceAvailable = (uiState.spaces as? UiState.Content)
        ?.value
        ?.any { space -> space.id == acceptedSpaceId } == true
    LaunchedEffect(acceptedSpaceId, acceptedSpaceAvailable) {
        if (acceptedSpaceId != null && acceptedSpaceAvailable) {
            onSpaceSelected(acceptedSpaceId)
            onConsumeAcceptedSpace()
            navController.navigate(BobitosDestination.Shopping.route) {
                popUpTo(BobitosDestination.Spaces.route) { inclusive = true }
            }
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
            RootScaffold(
                currentDestination = BobitosDestination.Spaces,
                onDestinationSelected = navController::navigateToRoot,
            ) {
                SpacesScreen(
                    state = uiState.spaces,
                    managementState = spaceManagementState,
                    syncStatus = uiState.syncStatus,
                    canWrite = uiState.syncStatus.canWrite,
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
                    onAcceptInvitation = onAcceptInvitation,
                    pendingInvitationCode = pendingInvitationCode,
                    onInvitationCodeConsumed = onInvitationCodeConsumed,
                    onClearFeedback = onClearSpaceFeedback,
                )
            }
        }

        composable(BobitosDestination.MyCalendar.route) {
            RootScaffold(
                currentDestination = BobitosDestination.MyCalendar,
                onDestinationSelected = navController::navigateToRoot,
            ) {
                PersonalCalendarScreen(
                    userId = authUser.id,
                    spaces = (uiState.spaces as? UiState.Content)?.value.orEmpty(),
                    syncStatus = uiState.syncStatus,
                    onEventSelected = { eventSpaceId, eventId, date ->
                        onSpaceSelected(eventSpaceId)
                        navController.navigate(
                            "calendar-event/${Uri.encode(eventId)}/$date",
                        )
                    },
                )
            }
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
                syncStatus = uiState.syncStatus,
            ) {
                uiState.selectedSpace?.let { space ->
                    ShoppingScreen(
                        spaceId = space.id,
                        canWrite = uiState.syncStatus.canWrite,
                    )
                }
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
                syncStatus = uiState.syncStatus,
            ) {
                uiState.selectedSpace?.let { space ->
                    TasksScreen(
                        spaceId = space.id,
                        canWrite = uiState.syncStatus.canWrite,
                    )
                }
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
                syncStatus = uiState.syncStatus,
            ) {
                uiState.selectedSpace?.let { space ->
                    CalendarScreen(spaceId = space.id, canWrite = uiState.syncStatus.canWrite)
                }
            }
        }

        composable(
            route = CALENDAR_EVENT_ROUTE,
            arguments = listOf(
                navArgument("eventId") { type = NavType.StringType },
                navArgument("date") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
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
                syncStatus = uiState.syncStatus,
            ) {
                uiState.selectedSpace?.let { space ->
                    CalendarScreen(
                        spaceId = space.id,
                        canWrite = uiState.syncStatus.canWrite,
                        initialEventId = backStackEntry.arguments?.getString("eventId"),
                        initialDate = backStackEntry.arguments?.getString("date")
                            ?.let(LocalDate::parse),
                    )
                }
            }
        }

        composable(BobitosDestination.Profile.route) {
            ProfileScreen(
                user = authUser,
                actionState = authActionState,
                syncStatus = uiState.syncStatus,
                canWrite = uiState.syncStatus.canWrite,
                onUpdateDisplayName = onUpdateDisplayName,
                onSignOut = onSignOut,
                onDeleteAccount = onDeleteAccount,
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
                    syncStatus = uiState.syncStatus,
                    canWrite = uiState.syncStatus.canWrite,
                    onObserveSpaceSettings = onObserveSpaceSettings,
                    onStopObservingSpaceSettings = onStopObservingSpaceSettings,
                    onRenameSpace = onRenameSpace,
                    onLeaveSpace = onLeaveSpace,
                    onRemoveMember = onRemoveMember,
                    onTransferOwnership = onTransferOwnership,
                    onDeleteSpace = { spaceId ->
                        onDeleteSpace(spaceId)
                        navController.navigateToSpaces()
                    },
                    onCreateInvitation = onCreateInvitation,
                    onRevokeInvitation = onRevokeInvitation,
                    onShareInvitation = onShareInvitation,
                    onClearFeedback = onClearSpaceFeedback,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun RootScaffold(
    currentDestination: BobitosDestination,
    onDestinationSelected: (BobitosDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                BobitosDestination.rootDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == currentDestination,
                        onClick = { onDestinationSelected(destination) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.titleRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) { content() }
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
    syncStatus: SyncStatus,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
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
                        IconButton(onClick = onProfile) {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = stringResource(R.string.profile_open),
                            )
                        }
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.space_settings)) },
                                onClick = {
                                    menuExpanded = false
                                    onSpaceSettings()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.change_space)) },
                                onClick = {
                                    menuExpanded = false
                                    onSwitchSpace()
                                },
                            )
                        }
                    },
                )
                SyncStatusBanner(syncStatus)
            }
        },
        bottomBar = {
            NavigationBar {
                BobitosDestination.workspaceDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == currentDestination,
                        onClick = { onDestinationSelected(destination) },
                        icon = { Icon(destination.icon, contentDescription = null) },
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

private fun NavHostController.navigateToRoot(destination: BobitosDestination) {
    navigate(destination.route) {
        popUpTo(BobitosDestination.Spaces.route) { saveState = true }
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

private const val CALENDAR_EVENT_ROUTE = "calendar-event/{eventId}/{date}"
