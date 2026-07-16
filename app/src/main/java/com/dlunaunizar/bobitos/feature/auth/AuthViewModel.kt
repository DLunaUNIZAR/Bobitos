package com.dlunaunizar.bobitos.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dlunaunizar.bobitos.data.repository.AuthFailure
import com.dlunaunizar.bobitos.data.repository.AuthRepository
import com.dlunaunizar.bobitos.data.repository.AuthRepositoryException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AuthActionUiState())
    val uiState: StateFlow<AuthActionUiState> = mutableUiState.asStateFlow()
    private var verificationResendCooldownJob: Job? = null

    fun register(
        displayName: String,
        email: String,
        password: String,
        passwordConfirmation: String,
    ) {
        val error = AuthValidation.validateRegistration(
            displayName = displayName,
            email = email,
            password = password,
            passwordConfirmation = passwordConfirmation,
        )
        if (error != null) {
            showValidationError(error)
            return
        }

        runAction(onSuccess = ::startVerificationResendCooldown) {
            authRepository.register(
                displayName = displayName.trim(),
                email = email.trim(),
                password = password,
            )
        }
    }

    fun signIn(
        email: String,
        password: String,
    ) {
        val error = AuthValidation.validateSignIn(email, password)
        if (error != null) {
            showValidationError(error)
            return
        }

        runAction {
            authRepository.signIn(
                email = email.trim(),
                password = password,
            )
        }
    }

    fun sendPasswordReset(email: String) {
        val error = AuthValidation.validateEmail(email)
        if (error != null) {
            showValidationError(error)
            return
        }

        runAction(successNotice = AuthUiMessage.PasswordResetRequested) {
            authRepository.sendPasswordReset(email.trim())
        }
    }

    fun resendVerificationEmail() {
        if (mutableUiState.value.verificationResendSecondsRemaining > 0) return

        runAction(
            successNotice = AuthUiMessage.VerificationEmailSent,
            onSuccess = ::startVerificationResendCooldown,
        ) {
            authRepository.sendEmailVerification()
        }
    }

    fun refreshEmailVerification() {
        if (mutableUiState.value.isLoading) return

        mutableUiState.update { state ->
            state.copy(isLoading = true, error = null, notice = null)
        }
        viewModelScope.launch {
            try {
                val user = authRepository.refreshCurrentUser()
                mutableUiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = null,
                        notice = if (user.isEmailVerified) {
                            null
                        } else {
                            AuthUiMessage.EmailStillNotVerified
                        },
                    )
                }
            } catch (error: Throwable) {
                mutableUiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = error.toUiMessage(),
                        notice = null,
                    )
                }
            }
        }
    }

    fun updateDisplayName(displayName: String) {
        val error = AuthValidation.validateDisplayName(displayName)
        if (error != null) {
            showValidationError(error)
            return
        }

        runAction(successNotice = AuthUiMessage.ProfileUpdated) {
            authRepository.updateDisplayName(displayName.trim())
        }
    }

    fun signOut() {
        verificationResendCooldownJob?.cancel()
        authRepository.signOut()
        mutableUiState.value = AuthActionUiState()
    }

    fun clearFeedback() {
        mutableUiState.update { state ->
            state.copy(error = null, notice = null)
        }
    }

    private fun showValidationError(error: AuthUiMessage) {
        mutableUiState.update { state ->
            state.copy(isLoading = false, error = error, notice = null)
        }
    }

    private fun runAction(
        successNotice: AuthUiMessage? = null,
        onSuccess: () -> Unit = {},
        action: suspend () -> Unit,
    ) {
        if (mutableUiState.value.isLoading) return

        mutableUiState.update { state ->
            state.copy(isLoading = true, error = null, notice = null)
        }
        viewModelScope.launch {
            try {
                action()
                mutableUiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = null,
                        notice = successNotice,
                    )
                }
                onSuccess()
            } catch (error: Throwable) {
                mutableUiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = error.toUiMessage(),
                        notice = null,
                    )
                }
            }
        }
    }

    private fun startVerificationResendCooldown() {
        verificationResendCooldownJob?.cancel()
        mutableUiState.update { state ->
            state.copy(
                verificationResendSecondsRemaining = VERIFICATION_RESEND_COOLDOWN_SECONDS,
            )
        }
        verificationResendCooldownJob = viewModelScope.launch {
            repeat(VERIFICATION_RESEND_COOLDOWN_SECONDS) {
                delay(1_000)
                mutableUiState.update { state ->
                    state.copy(
                        verificationResendSecondsRemaining =
                            (state.verificationResendSecondsRemaining - 1).coerceAtLeast(0),
                    )
                }
            }
        }
    }
}

private const val VERIFICATION_RESEND_COOLDOWN_SECONDS = 60

private fun Throwable.toUiMessage(): AuthUiMessage {
    val failure = (this as? AuthRepositoryException)?.failure
    return when (failure) {
        AuthFailure.EmailAlreadyInUse -> AuthUiMessage.EmailAlreadyInUse
        AuthFailure.InvalidEmail -> AuthUiMessage.InvalidEmail
        AuthFailure.WeakPassword -> AuthUiMessage.PasswordTooShort
        AuthFailure.InvalidCredentials -> AuthUiMessage.InvalidCredentials
        AuthFailure.Network -> AuthUiMessage.NetworkError
        AuthFailure.TooManyRequests -> AuthUiMessage.TooManyRequests
        AuthFailure.NoAuthenticatedUser,
        AuthFailure.Unknown,
        null -> AuthUiMessage.UnexpectedError
    }
}
