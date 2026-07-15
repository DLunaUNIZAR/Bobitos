package com.dlunaunizar.bobitos.feature.auth

internal object AuthValidation {
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_DISPLAY_NAME_LENGTH = 60

    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun validateDisplayName(displayName: String): AuthUiMessage? = when {
        displayName.trim().isEmpty() -> AuthUiMessage.DisplayNameRequired
        displayName.trim().length > MAX_DISPLAY_NAME_LENGTH -> {
            AuthUiMessage.DisplayNameTooLong
        }
        else -> null
    }

    fun validateEmail(email: String): AuthUiMessage? = when {
        !emailPattern.matches(email.trim()) -> AuthUiMessage.InvalidEmail
        else -> null
    }

    fun validatePassword(password: String): AuthUiMessage? = when {
        password.length < MIN_PASSWORD_LENGTH -> AuthUiMessage.PasswordTooShort
        else -> null
    }

    fun validateRegistration(
        displayName: String,
        email: String,
        password: String,
        passwordConfirmation: String,
    ): AuthUiMessage? = validateDisplayName(displayName)
        ?: validateEmail(email)
        ?: validatePassword(password)
        ?: if (password != passwordConfirmation) {
            AuthUiMessage.PasswordsDoNotMatch
        } else {
            null
        }

    fun validateSignIn(
        email: String,
        password: String,
    ): AuthUiMessage? = validateEmail(email)
        ?: if (password.isBlank()) AuthUiMessage.InvalidCredentials else null
}
