package com.dlunaunizar.bobitos.data.repository

interface AccountRepository {
    suspend fun deleteAccount(password: String)
}

enum class AccountFailure {
    PasswordRequired, InvalidCredentials, OwnerSpacesRemaining, NotAuthenticated,
    PermissionDenied, Network, Unknown,
}

class AccountRepositoryException(
    val failure: AccountFailure,
    cause: Throwable? = null,
) : Exception(cause)
