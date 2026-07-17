package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentUser: StateFlow<AuthUser?>

    suspend fun register(
        displayName: String,
        email: String,
        password: String,
    )

    suspend fun signIn(
        email: String,
        password: String,
    )

    suspend fun sendEmailVerification()

    suspend fun refreshCurrentUser(): AuthUser

    suspend fun sendPasswordReset(email: String)

    suspend fun updateDisplayName(displayName: String)

    fun signOut()
}

enum class AuthFailure {
    EmailAlreadyInUse,
    InvalidEmail,
    WeakPassword,
    InvalidCredentials,
    Network,
    TooManyRequests,
    SessionExpired,
    NoAuthenticatedUser,
    Unknown,
}

class AuthRepositoryException(
    val failure: AuthFailure,
    cause: Throwable? = null,
) : Exception(cause)
