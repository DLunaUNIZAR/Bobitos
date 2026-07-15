package com.dlunaunizar.bobitos.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

private enum class AuthDestination(val route: String) {
    Login("auth/login"),
    Register("auth/register"),
    PasswordReset("auth/password-reset"),
}

@Composable
fun AuthNavHost(
    navController: NavHostController,
    actionState: AuthActionUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onRegister: (
        displayName: String,
        email: String,
        password: String,
        passwordConfirmation: String,
    ) -> Unit,
    onPasswordReset: (email: String) -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AuthDestination.Login.route,
        modifier = modifier,
    ) {
        composable(AuthDestination.Login.route) {
            LoginScreen(
                actionState = actionState,
                onSignIn = onSignIn,
                onRegisterClick = {
                    onClearFeedback()
                    navController.navigate(AuthDestination.Register.route)
                },
                onPasswordResetClick = {
                    onClearFeedback()
                    navController.navigate(AuthDestination.PasswordReset.route)
                },
                onClearFeedback = onClearFeedback,
            )
        }
        composable(AuthDestination.Register.route) {
            RegisterScreen(
                actionState = actionState,
                onRegister = onRegister,
                onLoginClick = {
                    onClearFeedback()
                    navController.popBackStack()
                },
                onClearFeedback = onClearFeedback,
            )
        }
        composable(AuthDestination.PasswordReset.route) {
            PasswordResetScreen(
                actionState = actionState,
                onPasswordReset = onPasswordReset,
                onBackToLogin = {
                    onClearFeedback()
                    navController.popBackStack()
                },
                onClearFeedback = onClearFeedback,
            )
        }
    }
}
