package com.dlunaunizar.bobitos.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.rememberNavController
import com.dlunaunizar.bobitos.R
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.navigation.BobitosNavHost
import com.dlunaunizar.bobitos.feature.auth.AuthActionUiState
import com.dlunaunizar.bobitos.feature.auth.AuthNavHost
import com.dlunaunizar.bobitos.feature.auth.EmailVerificationScreen
import com.dlunaunizar.bobitos.feature.auth.FullScreenLoading
import com.dlunaunizar.bobitos.feature.spaces.SpaceManagementUiState
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.feature.shopping.ShoppingUiState
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.feature.tasks.TaskFilters
import com.dlunaunizar.bobitos.feature.tasks.TasksUiState
import java.time.Instant

@Composable
fun BobitosApp(
    uiState: AppUiState,
    authActionState: AuthActionUiState,
    spaceManagementState: SpaceManagementUiState,
    shoppingState: ShoppingUiState,
    tasksState: TasksUiState,
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
    onObserveShopping: (String) -> Unit,
    onStopObservingShopping: () -> Unit,
    onAddShoppingItem: (String, String, String?, String?) -> Unit,
    onUpdateShoppingItem: (String, String, String, String?, String?) -> Unit,
    onSetShoppingItemPurchased: (String, String, Boolean) -> Unit,
    onDeleteShoppingItem: (String, String) -> Unit,
    onClearPurchasedShoppingItems: (String) -> Unit,
    onClearShoppingFeedback: () -> Unit,
    onObserveTasks: (String) -> Unit,
    onStopObservingTasks: () -> Unit,
    onTaskFiltersChanged: (TaskFilters) -> Unit,
    onCreateTask: (String, String, String?, String?, Instant?, TaskPriority) -> Unit,
    onUpdateTask: (String, String, String, String?, String?, Instant?, TaskPriority) -> Unit,
    onSetTaskCompleted: (String, String, Boolean) -> Unit,
    onDeleteTask: (String, String) -> Unit,
    onInvalidTaskDate: () -> Unit,
    onClearTaskFeedback: () -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onRegister: (
        displayName: String,
        email: String,
        password: String,
        passwordConfirmation: String,
    ) -> Unit,
    onPasswordReset: (email: String) -> Unit,
    onRefreshVerification: () -> Unit,
    onResendVerification: () -> Unit,
    onUpdateDisplayName: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: (String) -> Unit,
    onClearAuthFeedback: () -> Unit,
) {
    when (val authState = uiState.authUser) {
        UiState.Loading -> FullScreenLoading()
        is UiState.Error -> AppLoadError(authState.message)
        is UiState.Content -> {
            val user = authState.value
            when {
                user == null -> key("unauthenticated") {
                    AuthNavHost(
                        navController = rememberNavController(),
                        actionState = authActionState,
                        onSignIn = onSignIn,
                        onRegister = onRegister,
                        onPasswordReset = onPasswordReset,
                        onClearFeedback = onClearAuthFeedback,
                    )
                }
                !user.isEmailVerified -> key("email-verification") {
                    EmailVerificationScreen(
                        user = user,
                        actionState = authActionState,
                        onRefreshVerification = onRefreshVerification,
                        onResendVerification = onResendVerification,
                        onSignOut = onSignOut,
                    )
                }
                else -> key("authenticated-${user.id}") {
                    when (uiState.spaces) {
                        UiState.Loading -> FullScreenLoading()
                        is UiState.Error -> AppLoadError(uiState.spaces.message)
                        is UiState.Content -> BobitosNavHost(
                            navController = rememberNavController(),
                            uiState = uiState,
                            authUser = user,
                            authActionState = authActionState,
                            spaceManagementState = spaceManagementState,
                            shoppingState = shoppingState,
                            tasksState = tasksState,
                            onSpaceSelected = onSpaceSelected,
                            onRealtimeScopeChanged = onRealtimeScopeChanged,
                            onCreateSpace = onCreateSpace,
                            onObserveSpaceSettings = onObserveSpaceSettings,
                            onStopObservingSpaceSettings = onStopObservingSpaceSettings,
                            onRenameSpace = onRenameSpace,
                            onLeaveSpace = onLeaveSpace,
                            onRemoveMember = onRemoveMember,
                            onTransferOwnership = onTransferOwnership,
                            onDeleteSpace = onDeleteSpace,
                            onCreateInvitation = onCreateInvitation,
                            onRevokeInvitation = onRevokeInvitation,
                            onAcceptInvitation = onAcceptInvitation,
                            onShareInvitation = onShareInvitation,
                            onConsumeAcceptedSpace = onConsumeAcceptedSpace,
                            pendingInvitationCode = pendingInvitationCode,
                            onInvitationCodeConsumed = onInvitationCodeConsumed,
                            onClearSpaceFeedback = onClearSpaceFeedback,
                            onObserveShopping = onObserveShopping,
                            onStopObservingShopping = onStopObservingShopping,
                            onAddShoppingItem = onAddShoppingItem,
                            onUpdateShoppingItem = onUpdateShoppingItem,
                            onSetShoppingItemPurchased = onSetShoppingItemPurchased,
                            onDeleteShoppingItem = onDeleteShoppingItem,
                            onClearPurchasedShoppingItems = onClearPurchasedShoppingItems,
                            onClearShoppingFeedback = onClearShoppingFeedback,
                            onObserveTasks = onObserveTasks,
                            onStopObservingTasks = onStopObservingTasks,
                            onTaskFiltersChanged = onTaskFiltersChanged,
                            onCreateTask = onCreateTask,
                            onUpdateTask = onUpdateTask,
                            onSetTaskCompleted = onSetTaskCompleted,
                            onDeleteTask = onDeleteTask,
                            onInvalidTaskDate = onInvalidTaskDate,
                            onClearTaskFeedback = onClearTaskFeedback,
                            onUpdateDisplayName = onUpdateDisplayName,
                            onSignOut = onSignOut,
                            onDeleteAccount = onDeleteAccount,
                            onClearAuthFeedback = onClearAuthFeedback,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLoadError(message: String?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message ?: stringResource(R.string.app_start_error))
    }
}
