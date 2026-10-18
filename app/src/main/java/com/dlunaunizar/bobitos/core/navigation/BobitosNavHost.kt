package com.dlunaunizar.bobitos.core.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.dlunaunizar.bobitos.core.common.BobitosDates
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.designsystem.component.LocalSnackbarHostState
import com.dlunaunizar.bobitos.core.designsystem.component.launchUndo
import com.dlunaunizar.bobitos.core.designsystem.rememberReduceMotion
import com.dlunaunizar.bobitos.core.designsystem.rememberToday
import com.dlunaunizar.bobitos.core.designsystem.theme.AppModule
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
import com.dlunaunizar.bobitos.feature.pantry.PantryScreen
import com.dlunaunizar.bobitos.feature.recipes.RecipesScreen
import com.dlunaunizar.bobitos.feature.routines.RoutinesScreen
import com.dlunaunizar.bobitos.feature.shopping.ShoppingScreen
import com.dlunaunizar.bobitos.feature.spaces.DayPeriod
import com.dlunaunizar.bobitos.feature.spaces.SpaceHomeViewModel
import com.dlunaunizar.bobitos.feature.spaces.SpaceManagementUiState
import com.dlunaunizar.bobitos.feature.spaces.SpaceSettingsScreen
import com.dlunaunizar.bobitos.feature.spaces.SpacesScreen
import com.dlunaunizar.bobitos.feature.spaces.TodayAgenda
import com.dlunaunizar.bobitos.feature.spaces.dayPeriodFor
import com.dlunaunizar.bobitos.feature.spaces.firstName
import com.dlunaunizar.bobitos.feature.sport.SportScreen
import com.dlunaunizar.bobitos.feature.tasks.TasksScreen
import com.dlunaunizar.bobitos.feature.tasks.stringRes
import java.time.LocalDate
import java.time.LocalDateTime

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
    onEditorSaveConsumed: () -> Unit,
    onUpdateDisplayName: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: (String) -> Unit,
    onClearAuthFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spaceName = uiState.selectedSpace?.name ?: stringResource(R.string.app_name)
    // El selector de espacio vive aquí para poder pedir todos los espacios mientras está abierto.
    var spacePickerOpen by rememberSaveable { mutableStateOf(false) }
    // Cambiar de espacio desde la barra: se selecciona y se vuelve a «Hoy» (los datos son del nuevo espacio).
    // Se recuerda para no crear un objeto nuevo en cada recomposición (forzaría a reconstruir el grafo).
    val spaces = (uiState.spaces as? UiState.Content)?.value.orEmpty()
    val selectedSpaceId = uiState.selectedSpace?.id
    val spaceSwitcher = remember(spaces, selectedSpaceId, spacePickerOpen) {
        SpaceSwitcher(
            spaces = spaces,
            selectedSpaceId = selectedSpaceId,
            pickerOpen = spacePickerOpen,
            onPickerOpenChange = { spacePickerOpen = it },
            onSelect = { space ->
                onClearSpaceFeedback()
                onSpaceSelected(space.id)
                navController.popBackStack(BobitosDestination.SpaceHome.route, inclusive = false)
            },
        )
    }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val protectedRoutes = BobitosDestination.workspaceDestinations.map { it.route } +
        BobitosDestination.SpaceSettings.route +
        BobitosDestination.SpaceHome.route +
        BobitosDestination.SpaceMore.route +
        BobitosDestination.Notes.route +
        BobitosDestination.Pantry.route +
        CALENDAR_EVENT_ROUTE

    LaunchedEffect(currentRoute, spacePickerOpen) {
        onRealtimeScopeChanged(realtimeScopeFor(currentRoute, spacePickerOpen))
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
                spaces = uiState.spaces,
                syncStatus = uiState.syncStatus,
                onEnterSpace = {
                    onSpaceSelected(it.id)
                    navController.navigate(BobitosDestination.SpaceHome.route) { launchSingleTop = true }
                },
                onManageSpaces = navController::navigateToSpaces,
                onOpen = { destination -> navController.navigate(destination.route) },
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
                onEditorSaveConsumed = onEditorSaveConsumed,
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
            val agenda by summaryViewModel.agenda.collectAsStateWithLifecycle()
            LaunchedEffect(uiState.selectedSpace?.id) {
                uiState.selectedSpace?.id?.let { summaryViewModel.show(it, authUser.id) }
            }
            // Fecha y saludo siguen a `rememberToday()`: a medianoche se renuevan.
            val today = rememberToday()
            val greeting =
                stringResource(greetingRes(dayPeriodFor(remember(agenda, today) { LocalDateTime.now().hour })))
            val name = firstName(authUser.displayName)
            SpaceScaffold(
                navController = navController,
                selectedTab = BobitosDestination.SpaceHome,
                module = null,
                title = BobitosDates.longDay(today),
                supportingText = if (name.isBlank()) {
                    greeting
                } else {
                    stringResource(R.string.space_today_greeting_named, greeting, name)
                },
                spaceName = spaceName,
                switcher = spaceSwitcher,
                syncStatus = uiState.syncStatus,
                onClearSpaceFeedback = onClearSpaceFeedback,
                onClearAuthFeedback = onClearAuthFeedback,
            ) {
                SpaceHomeContent(
                    viewModel = summaryViewModel,
                    agenda = agenda,
                    canWrite = uiState.syncStatus.canWrite,
                    onOpen = { navController.navigateToWorkspace(it) },
                )
            }
        }

        composable(BobitosDestination.SpaceMore.route) {
            SpaceScaffold(
                navController = navController,
                selectedTab = BobitosDestination.SpaceMore,
                module = null,
                title = stringResource(R.string.space_more_title),
                spaceName = spaceName,
                switcher = spaceSwitcher,
                syncStatus = uiState.syncStatus,
                onClearSpaceFeedback = onClearSpaceFeedback,
                onClearAuthFeedback = onClearAuthFeedback,
            ) {
                SpaceMoreScreen(onOpen = navController::openFromMore)
            }
        }

        composable(BobitosDestination.Shopping.route) { entry ->
            val prefill by entry.savedStateHandle.getStateFlow<String?>(SHOPPING_PREFILL_KEY, null)
                .collectAsStateWithLifecycle()
            SpaceScaffold(
                navController = navController,
                selectedTab = workspaceTabFor(BobitosDestination.Shopping.route) ?: BobitosDestination.Shopping,
                module = AppModule.SHOPPING,
                title = stringResource(BobitosDestination.Shopping.titleRes),
                spaceName = spaceName,
                switcher = spaceSwitcher,
                syncStatus = uiState.syncStatus,
                onClearSpaceFeedback = onClearSpaceFeedback,
                onClearAuthFeedback = onClearAuthFeedback,
            ) {
                uiState.selectedSpace?.let { space ->
                    ShoppingScreen(
                        spaceId = space.id,
                        canWrite = uiState.syncStatus.canWrite,
                        memberCount = space.memberCount,
                        prefillName = prefill,
                        onPrefillConsumed = { entry.savedStateHandle[SHOPPING_PREFILL_KEY] = null },
                    )
                }
            }
        }

        composable(BobitosDestination.Tasks.route) {
            SpaceScaffold(
                navController = navController,
                selectedTab = workspaceTabFor(BobitosDestination.Tasks.route) ?: BobitosDestination.Tasks,
                module = AppModule.TASKS,
                title = stringResource(BobitosDestination.Tasks.titleRes),
                spaceName = spaceName,
                switcher = spaceSwitcher,
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
                module = AppModule.CALENDAR,
                title = stringResource(BobitosDestination.Calendar.titleRes),
                spaceName = spaceName,
                switcher = spaceSwitcher,
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
                module = AppModule.MEALS,
                title = stringResource(BobitosDestination.Meals.titleRes),
                spaceName = spaceName,
                switcher = spaceSwitcher,
                syncStatus = uiState.syncStatus,
                onClearSpaceFeedback = onClearSpaceFeedback,
                onClearAuthFeedback = onClearAuthFeedback,
            ) {
                uiState.selectedSpace?.let { space ->
                    MealsScreen(
                        spaceId = space.id,
                        canWrite = uiState.syncStatus.canWrite,
                    )
                }
            }
        }

        composable(BobitosDestination.Sport.route) {
            SpaceScaffold(
                navController = navController,
                selectedTab = workspaceTabFor(BobitosDestination.Sport.route) ?: BobitosDestination.Sport,
                module = AppModule.SPORT,
                title = stringResource(BobitosDestination.Sport.titleRes),
                spaceName = spaceName,
                switcher = spaceSwitcher,
                syncStatus = uiState.syncStatus,
                onClearSpaceFeedback = onClearSpaceFeedback,
                onClearAuthFeedback = onClearAuthFeedback,
            ) {
                uiState.selectedSpace?.let { space ->
                    SportScreen(
                        spaceId = space.id,
                        canWrite = uiState.syncStatus.canWrite,
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
                module = AppModule.CALENDAR,
                title = stringResource(BobitosDestination.Calendar.titleRes),
                spaceName = spaceName,
                switcher = spaceSwitcher,
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

        composable(BobitosDestination.Recipes.route) { entry ->
            val pantryFilter by entry.savedStateHandle.getStateFlow(RECIPES_PANTRY_FILTER_KEY, false)
                .collectAsStateWithLifecycle()
            RecipesScreen(
                onBack = { navController.popBackStack() },
                canWrite = uiState.syncStatus.canWrite,
                spaceId = uiState.selectedSpace?.id,
                importUrl = pendingRecipeImportUrl,
                onImportUrlConsumed = onRecipeImportUrlConsumed,
                pantryFilterRequested = pantryFilter,
                onPantryFilterConsumed = { entry.savedStateHandle[RECIPES_PANTRY_FILTER_KEY] = false },
            )
        }

        composable(BobitosDestination.Ingredients.route) {
            IngredientsScreen(
                onBack = { navController.popBackStack() },
                onOpenIngredient = { id -> navController.navigate("ingredient-detail/${Uri.encode(id)}") },
                onOpenBrand = { id, brandId ->
                    navController.navigate("ingredient-detail/${Uri.encode(id)}?brandId=${Uri.encode(brandId)}")
                },
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

        composable(BobitosDestination.Pantry.route) {
            PantryScreen(
                onBack = { navController.popBackStack() },
                canWrite = uiState.syncStatus.canWrite,
                onWhatToCook = { navController.openRecipesWithPantry() },
                spaceId = uiState.selectedSpace?.id,
            )
        }

        composable(
            route = INGREDIENT_DETAIL_ROUTE,
            arguments = listOf(
                navArgument("ingredientId") { type = NavType.StringType },
                navArgument("brandId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            IngredientDetailScreen(
                ingredientId = backStackEntry.arguments?.getString("ingredientId").orEmpty(),
                brandId = backStackEntry.arguments?.getString("brandId"),
                onBack = { navController.popBackStack() },
                onAddToShopping = uiState.selectedSpace?.let { { name -> navController.openShoppingWithName(name) } },
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
                    onEditorSaveConsumed = onEditorSaveConsumed,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

// Marco de las pantallas del espacio con el cableado de navegación común.
@Composable
private fun SpaceScaffold(
    navController: NavHostController,
    selectedTab: BobitosDestination,
    module: AppModule?,
    title: String,
    spaceName: String,
    switcher: SpaceSwitcher,
    syncStatus: SyncStatus,
    onClearSpaceFeedback: () -> Unit,
    onClearAuthFeedback: () -> Unit,
    supportingText: String? = null,
    content: @Composable () -> Unit,
) {
    WorkspaceScaffold(
        selectedTab = selectedTab,
        module = module,
        title = title,
        spaceName = spaceName,
        onTabSelected = navController::navigateToTab,
        switcher = switcher,
        onManageSpaces = navController::navigateToSpaces,
        onSpaceSettings = {
            onClearSpaceFeedback()
            navController.navigate(BobitosDestination.SpaceSettings.route)
        },
        onProfile = {
            onClearAuthFeedback()
            navController.navigateToProfile()
        },
        syncStatus = syncStatus,
        supportingText = supportingText,
        content = content,
    )
}

private fun greetingRes(period: DayPeriod): Int = when (period) {
    DayPeriod.MORNING -> R.string.space_today_greeting_morning
    DayPeriod.AFTERNOON -> R.string.space_today_greeting_afternoon
    DayPeriod.EVENING -> R.string.space_today_greeting_evening
}

// Va dentro del marco para leer su snackbar: completar avisa con «Deshacer» y los errores salen por el mismo sitio.
@Composable
private fun SpaceHomeContent(
    viewModel: SpaceHomeViewModel,
    agenda: UiState<TodayAgenda>,
    canWrite: Boolean,
    onOpen: (BobitosDestination) -> Unit,
) {
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val completedMessage = stringResource(R.string.tasks_undo_completed)
    val undoLabel = stringResource(R.string.undo)
    val error by viewModel.error.collectAsStateWithLifecycle()
    error?.let { message ->
        val text = stringResource(message.stringRes())
        LaunchedEffect(message) {
            snackbar?.showSnackbar(text)
            viewModel.clearError()
        }
    }
    SpaceTodayScreen(
        agenda = agenda,
        canWrite = canWrite,
        onToggleTask = { task ->
            viewModel.complete(task) {
                scope.launchUndo(snackbar, completedMessage, undoLabel) { viewModel.undoCompletion(it) }
            }
        },
        onRetry = viewModel::retry,
        onOpen = onOpen,
    )
}

internal const val SHOPPING_PREFILL_KEY = "shoppingPrefillName"
internal const val RECIPES_PANTRY_FILTER_KEY = "recipesPantryFilter"

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

// Entra en la Compra del espacio activo y le pasa el nombre a rellenar por el estado de su entrada
// (la ruta "shopping" no cambia: se compara como cadena en varios sitios).
private fun NavHostController.openShoppingWithName(name: String) {
    // Desde fuera del espacio (p. ej. Ingredientes del menú principal) se ancla antes el hub del espacio:
    // sin él, popUpTo(SpaceHome) se ignora y «Hoy» y las pestañas no funcionan.
    if (needsSpaceHomeAnchor(currentBackStack.value.map { it.destination.route })) {
        navigate(BobitosDestination.SpaceHome.route) { launchSingleTop = true }
    }
    navigateToWorkspace(BobitosDestination.Shopping)
    getBackStackEntry(BobitosDestination.Shopping.route).savedStateHandle[SHOPPING_PREFILL_KEY] = name
}

// «¿Qué cocino?» de la Despensa: abre el Recetario con «Con lo que hay» activado (la pantalla lo consume).
private fun NavHostController.openRecipesWithPantry() {
    navigate(BobitosDestination.Recipes.route)
    getBackStackEntry(BobitosDestination.Recipes.route).savedStateHandle[RECIPES_PANTRY_FILTER_KEY] = true
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

private const val NAV_ANIM_MS = 220

/** Falta el hub del espacio en la pila: hay que apilarlo antes de entrar en un módulo del espacio. */
internal fun needsSpaceHomeAnchor(backStackRoutes: List<String?>): Boolean =
    BobitosDestination.SpaceHome.route !in backStackRoutes
