package com.dlunaunizar.bobitos.core.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import com.dlunaunizar.bobitos.core.designsystem.component.BobitosTopBar
import com.dlunaunizar.bobitos.core.designsystem.component.SyncStatusBanner
import com.dlunaunizar.bobitos.core.designsystem.rememberReduceMotion
import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SyncStatus
import com.dlunaunizar.bobitos.core.model.canWrite
import com.dlunaunizar.bobitos.feature.auth.AuthActionUiState
import com.dlunaunizar.bobitos.feature.auth.ProfileScreen
import com.dlunaunizar.bobitos.feature.calendar.CalendarScreen
import com.dlunaunizar.bobitos.feature.calendar.PersonalCalendarScreen
import com.dlunaunizar.bobitos.feature.exercises.ExercisesScreen
import com.dlunaunizar.bobitos.feature.ingredients.IngredientDetailScreen
import com.dlunaunizar.bobitos.feature.ingredients.IngredientsScreen
import com.dlunaunizar.bobitos.feature.meals.MealsScreen
import com.dlunaunizar.bobitos.feature.notes.NotesScreen
import com.dlunaunizar.bobitos.feature.recipes.RecipesScreen
import com.dlunaunizar.bobitos.feature.routines.RoutinesScreen
import com.dlunaunizar.bobitos.feature.shopping.ShoppingScreen
import com.dlunaunizar.bobitos.feature.spaces.SpaceHomeViewModel
import com.dlunaunizar.bobitos.feature.spaces.SpaceManagementUiState
import com.dlunaunizar.bobitos.feature.spaces.SpaceSettingsScreen
import com.dlunaunizar.bobitos.feature.spaces.SpacesScreen
import com.dlunaunizar.bobitos.feature.sport.SportScreen
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
    pendingRecipeImportUrl: String?,
    onRecipeImportUrlConsumed: () -> Unit,
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
        BobitosDestination.SpaceSettings.route +
        BobitosDestination.SpaceHome.route +
        BobitosDestination.SpaceMore.route +
        BobitosDestination.Notes.route +
        CALENDAR_EVENT_ROUTE

    LaunchedEffect(currentRoute) {
        onRealtimeScopeChanged(
            when (currentRoute) {
                null -> RealtimeScope.AUTOMATIC
                BobitosDestination.Home.route,
                BobitosDestination.Spaces.route,
                BobitosDestination.MyCalendar.route,
                -> RealtimeScope.ALL_SPACES
                BobitosDestination.Profile.route,
                BobitosDestination.Recipes.route,
                BobitosDestination.Ingredients.route,
                BobitosDestination.Exercises.route,
                BobitosDestination.Routines.route,
                INGREDIENT_DETAIL_ROUTE,
                -> RealtimeScope.PAUSED
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

    // Un enlace compartido desde el navegador abre el Recetario, que lanza la importación.
    LaunchedEffect(pendingRecipeImportUrl, currentRoute) {
        if (
            pendingRecipeImportUrl != null &&
            currentRoute != null &&
            currentRoute != BobitosDestination.Recipes.route
        ) {
            navController.navigate(BobitosDestination.Recipes.route) { launchSingleTop = true }
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
            navController.navigate(BobitosDestination.SpaceHome.route) {
                popUpTo(BobitosDestination.Spaces.route) { inclusive = true }
            }
        }
    }

    val reduceMotion = rememberReduceMotion()
    NavHost(
        navController = navController,
        startDestination = BobitosDestination.Home.route,
        modifier = modifier.fillMaxSize(),
        enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(NAV_ANIM_MS)) },
        exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(NAV_ANIM_MS)) },
        popEnterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(NAV_ANIM_MS)) },
        popExitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(NAV_ANIM_MS)) },
    ) {
        composable(BobitosDestination.Home.route) {
            MainMenuScreen(
                syncStatus = uiState.syncStatus,
                onOpenSpaces = { navController.navigate(BobitosDestination.Spaces.route) },
                onOpenRecipes = { navController.navigate(BobitosDestination.Recipes.route) },
                onOpenIngredients = { navController.navigate(BobitosDestination.Ingredients.route) },
                onOpenRoutines = { navController.navigate(BobitosDestination.Routines.route) },
                onOpenExercises = { navController.navigate(BobitosDestination.Exercises.route) },
                onOpenMyCalendar = { navController.navigate(BobitosDestination.MyCalendar.route) },
                onProfile = {
                    onClearAuthFeedback()
                    navController.navigateToProfile()
                },
            )
        }

        composable(BobitosDestination.Spaces.route) {
            SpacesScreen(
                state = uiState.spaces,
                managementState = spaceManagementState,
                syncStatus = uiState.syncStatus,
                canWrite = uiState.syncStatus.canWrite,
                onBack = { navController.popBackStack() },
                onProfileClick = {
                    onClearAuthFeedback()
                    navController.navigateToProfile()
                },
                onSpaceSelected = { space ->
                    onClearSpaceFeedback()
                    onSpaceSelected(space.id)
                    navController.navigate(BobitosDestination.SpaceHome.route) {
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

        composable(BobitosDestination.MyCalendar.route) {
            PersonalCalendarScreen(
                userId = authUser.id,
                spaces = (uiState.spaces as? UiState.Content)?.value.orEmpty(),
                syncStatus = uiState.syncStatus,
                canWrite = uiState.syncStatus.canWrite,
                onBack = { navController.popBackStack() },
                onEventSelected = { eventSpaceId, eventId, date ->
                    // Abrir un evento del calendario personal entra en su espacio: se ancla el hub del
                    // espacio bajo la vista del evento para que la barra inferior de módulos reemplace
                    // pestañas (popUpTo(SpaceHome)) en vez de acumular entradas.
                    onSpaceSelected(eventSpaceId)
                    navController.navigate(BobitosDestination.SpaceHome.route) {
                        popUpTo(BobitosDestination.MyCalendar.route) { inclusive = true }
                    }
                    navController.navigate("calendar-event/${Uri.encode(eventId)}/$date")
                },
            )
        }

        composable(BobitosDestination.SpaceHome.route) {
            BackHandler { navController.navigateToHome() }
            val summaryViewModel: SpaceHomeViewModel = hiltViewModel()
            val counts by summaryViewModel.counts.collectAsStateWithLifecycle()
            val digest by summaryViewModel.digest.collectAsStateWithLifecycle()
            LaunchedEffect(uiState.selectedSpace?.id) {
                // «Hoy» vuelve a componerse cada vez que se regresa a la pestaña: se recarga para no
                // mostrar contadores de antes de editar en otro módulo.
                uiState.selectedSpace?.id?.let { summaryViewModel.load(it, authUser.id, force = true) }
            }
            SpaceScaffold(
                navController = navController,
                selectedTab = BobitosDestination.SpaceHome,
                screenTitle = null,
                spaceName = spaceName,
                syncStatus = uiState.syncStatus,
                onClearSpaceFeedback = onClearSpaceFeedback,
                onClearAuthFeedback = onClearAuthFeedback,
            ) {
                SpaceTodayScreen(
                    displayName = authUser.displayName,
                    counts = counts,
                    digest = digest,
                    onOpen = { destination ->
                        if (destination in BobitosDestination.workspaceTabs) {
                            navController.navigateToTab(destination)
                        } else {
                            navController.openFromMore(destination)
                        }
                    },
                )
            }
        }

        composable(BobitosDestination.SpaceMore.route) {
            SpaceScaffold(
                navController = navController,
                selectedTab = BobitosDestination.SpaceMore,
                screenTitle = null,
                spaceName = spaceName,
                syncStatus = uiState.syncStatus,
                onClearSpaceFeedback = onClearSpaceFeedback,
                onClearAuthFeedback = onClearAuthFeedback,
            ) {
                SpaceMoreScreen(onOpen = navController::openFromMore)
            }
        }

        composable(BobitosDestination.Shopping.route) {
            SpaceScaffold(
                navController = navController,
                selectedTab = workspaceTabFor(BobitosDestination.Shopping.route) ?: BobitosDestination.Shopping,
                screenTitle = stringResource(BobitosDestination.Shopping.titleRes),
                spaceName = spaceName,
                syncStatus = uiState.syncStatus,
                onClearSpaceFeedback = onClearSpaceFeedback,
                onClearAuthFeedback = onClearAuthFeedback,
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
            SpaceScaffold(
                navController = navController,
                selectedTab = workspaceTabFor(BobitosDestination.Tasks.route) ?: BobitosDestination.Tasks,
                screenTitle = stringResource(BobitosDestination.Tasks.titleRes),
                spaceName = spaceName,
                syncStatus = uiState.syncStatus,
                onClearSpaceFeedback = onClearSpaceFeedback,
                onClearAuthFeedback = onClearAuthFeedback,
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
            SpaceScaffold(
                navController = navController,
                selectedTab = workspaceTabFor(BobitosDestination.Calendar.route) ?: BobitosDestination.Calendar,
                screenTitle = stringResource(BobitosDestination.Calendar.titleRes),
                spaceName = spaceName,
                syncStatus = uiState.syncStatus,
                onClearSpaceFeedback = onClearSpaceFeedback,
                onClearAuthFeedback = onClearAuthFeedback,
            ) {
                uiState.selectedSpace?.let { space ->
                    CalendarScreen(spaceId = space.id, canWrite = uiState.syncStatus.canWrite)
                }
            }
        }

        composable(BobitosDestination.Meals.route) {
            SpaceScaffold(
                navController = navController,
                selectedTab = workspaceTabFor(BobitosDestination.Meals.route) ?: BobitosDestination.Meals,
                screenTitle = stringResource(BobitosDestination.Meals.titleRes),
                spaceName = spaceName,
                syncStatus = uiState.syncStatus,
                onClearSpaceFeedback = onClearSpaceFeedback,
                onClearAuthFeedback = onClearAuthFeedback,
            ) {
                uiState.selectedSpace?.let { space ->
                    MealsScreen(
                        spaceId = space.id,
                        canWrite = uiState.syncStatus.canWrite,
                        onOpenRecipes = { navController.navigate(BobitosDestination.Recipes.route) },
                        onOpenIngredients = { navController.navigate(BobitosDestination.Ingredients.route) },
                    )
                }
            }
        }

        composable(BobitosDestination.Sport.route) {
            SpaceScaffold(
                navController = navController,
                selectedTab = workspaceTabFor(BobitosDestination.Sport.route) ?: BobitosDestination.Sport,
                screenTitle = stringResource(BobitosDestination.Sport.titleRes),
                spaceName = spaceName,
                syncStatus = uiState.syncStatus,
                onClearSpaceFeedback = onClearSpaceFeedback,
                onClearAuthFeedback = onClearAuthFeedback,
            ) {
                uiState.selectedSpace?.let { space ->
                    SportScreen(
                        spaceId = space.id,
                        canWrite = uiState.syncStatus.canWrite,
                        onOpenExercises = { navController.navigate(BobitosDestination.Exercises.route) },
                        onOpenRoutines = { navController.navigate(BobitosDestination.Routines.route) },
                    )
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
            SpaceScaffold(
                navController = navController,
                selectedTab = workspaceTabFor(BobitosDestination.Calendar.route) ?: BobitosDestination.Calendar,
                screenTitle = stringResource(BobitosDestination.Calendar.titleRes),
                spaceName = spaceName,
                syncStatus = uiState.syncStatus,
                onClearSpaceFeedback = onClearSpaceFeedback,
                onClearAuthFeedback = onClearAuthFeedback,
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

        composable(BobitosDestination.Recipes.route) {
            RecipesScreen(
                onBack = { navController.popBackStack() },
                canWrite = uiState.syncStatus.canWrite,
                spaceId = uiState.selectedSpace?.id,
                importUrl = pendingRecipeImportUrl,
                onImportUrlConsumed = onRecipeImportUrlConsumed,
            )
        }

        composable(BobitosDestination.Ingredients.route) {
            IngredientsScreen(
                onBack = { navController.popBackStack() },
                onOpenIngredient = { id -> navController.navigate("ingredient-detail/${Uri.encode(id)}") },
            )
        }

        composable(BobitosDestination.Exercises.route) {
            ExercisesScreen(onBack = { navController.popBackStack() })
        }

        composable(BobitosDestination.Routines.route) {
            RoutinesScreen(
                onBack = { navController.popBackStack() },
                canWrite = uiState.syncStatus.canWrite,
            )
        }

        composable(BobitosDestination.Notes.route) {
            NotesScreen(
                onBack = { navController.popBackStack() },
                canWrite = uiState.syncStatus.canWrite,
                spaceId = uiState.selectedSpace?.id,
            )
        }

        composable(
            route = INGREDIENT_DETAIL_ROUTE,
            arguments = listOf(navArgument("ingredientId") { type = NavType.StringType }),
        ) { backStackEntry ->
            IngredientDetailScreen(
                ingredientId = backStackEntry.arguments?.getString("ingredientId").orEmpty(),
                onBack = { navController.popBackStack() },
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

// Menú principal (al abrir la app): espacios + catálogos globales (recetario, ingredientes, rutinas,
// ejercicios) + calendario personal. Los catálogos no dependen de ningún espacio.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainMenuScreen(
    syncStatus: SyncStatus,
    onOpenSpaces: () -> Unit,
    onOpenRecipes: () -> Unit,
    onOpenIngredients: () -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenExercises: () -> Unit,
    onOpenMyCalendar: () -> Unit,
    onProfile: () -> Unit,
) {
    val onCardClick: (BobitosDestination) -> Unit = { destination ->
        when (destination) {
            BobitosDestination.Spaces -> onOpenSpaces()
            BobitosDestination.Recipes -> onOpenRecipes()
            BobitosDestination.Ingredients -> onOpenIngredients()
            BobitosDestination.Routines -> onOpenRoutines()
            BobitosDestination.Exercises -> onOpenExercises()
            BobitosDestination.MyCalendar -> onOpenMyCalendar()
            else -> Unit
        }
    }
    Scaffold(
        topBar = {
            Column {
                BobitosTopBar(
                    titleContent = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    },
                    actions = {
                        IconButton(onClick = onProfile) {
                            Icon(
                                Icons.Rounded.AccountCircle,
                                contentDescription = stringResource(R.string.profile_open),
                            )
                        }
                    },
                )
                SyncStatusBanner(syncStatus)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BobitosDestination.mainMenuDestinations.forEach { destination ->
                SpaceHomeCard(destination = destination, count = 0, onClick = { onCardClick(destination) })
            }
        }
    }
}

// Marco de las pantallas del espacio con el cableado de navegación común.
@Composable
private fun SpaceScaffold(
    navController: NavHostController,
    selectedTab: BobitosDestination,
    screenTitle: String?,
    spaceName: String,
    syncStatus: SyncStatus,
    onClearSpaceFeedback: () -> Unit,
    onClearAuthFeedback: () -> Unit,
    content: @Composable () -> Unit,
) {
    WorkspaceScaffold(
        selectedTab = selectedTab,
        screenTitle = screenTitle,
        spaceName = spaceName,
        onTabSelected = navController::navigateToTab,
        onSwitchSpace = navController::navigateToSpaces,
        onSpaceSettings = {
            onClearSpaceFeedback()
            navController.navigate(BobitosDestination.SpaceSettings.route)
        },
        onProfile = {
            onClearAuthFeedback()
            navController.navigateToProfile()
        },
        syncStatus = syncStatus,
        content = content,
    )
}

private fun NavHostController.navigateToWorkspace(destination: BobitosDestination) {
    navigate(destination.route) {
        popUpTo(BobitosDestination.SpaceHome.route) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateToTab(tab: BobitosDestination) {
    when (tab) {
        // «Hoy» es el ancla de la pila: basta con volver a ella.
        BobitosDestination.SpaceHome -> popBackStack(BobitosDestination.SpaceHome.route, inclusive = false)
        // «Más» siempre abre su propia pantalla: si ya está en la pila (estando en Comidas/Deporte) se
        // vuelve a ella; si no, se navega sin restaurar la cadena guardada, que aterrizaría en Comidas.
        BobitosDestination.SpaceMore ->
            if (!popBackStack(BobitosDestination.SpaceMore.route, inclusive = false)) {
                navigate(BobitosDestination.SpaceMore.route) {
                    popUpTo(BobitosDestination.SpaceHome.route) { saveState = true }
                    launchSingleTop = true
                }
            }
        else -> navigateToWorkspace(tab)
    }
}

// Comidas, Deporte y Notas se apilan sobre «Más» para que atrás vuelva a «Más».
private fun NavHostController.openFromMore(destination: BobitosDestination) {
    navigate(destination.route) { launchSingleTop = true }
}

private fun NavHostController.navigateToHome() {
    navigate(BobitosDestination.Home.route) {
        popUpTo(graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateToSpaces() {
    navigate(BobitosDestination.Spaces.route) {
        popUpTo(BobitosDestination.Home.route) { inclusive = false }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateToProfile() {
    navigate(BobitosDestination.Profile.route) {
        launchSingleTop = true
    }
}

private const val INGREDIENT_DETAIL_ROUTE = "ingredient-detail/{ingredientId}"
private const val NAV_ANIM_MS = 220
