package com.dlunaunizar.bobitos.feature.auth

data class AuthActionUiState(
    val isLoading: Boolean = false,
    val error: AuthUiMessage? = null,
    val notice: AuthUiMessage? = null,
    val verificationResendSecondsRemaining: Int = 0,
)

enum class AuthUiMessage {
    DisplayNameRequired,
    DisplayNameTooLong,
    InvalidEmail,
    PasswordTooShort,
    PasswordsDoNotMatch,
    EmailAlreadyInUse,
    InvalidCredentials,
    NetworkError,
    TooManyRequests,
    UnexpectedError,
    VerificationEmailSent,
    EmailStillNotVerified,
    PasswordResetRequested,
    ProfileUpdated,
    PasswordRequired,
    OwnerSpacesRemaining,
    AccountDeleted,
}
