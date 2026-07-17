package com.dlunaunizar.bobitos.data.repository

import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.data.firebase.FirebaseInitializer
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(firebaseInitializer: FirebaseInitializer) : AuthRepository {
    private val firebaseAuth = firebaseInitializer.auth()
    private val mutableCurrentUser = MutableStateFlow(firebaseAuth.currentUser?.toAuthUser())
    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        mutableCurrentUser.value = auth.currentUser?.toAuthUser()
    }

    override val currentUser: StateFlow<AuthUser?> = mutableCurrentUser.asStateFlow()

    init {
        firebaseAuth.useAppLanguage()
        firebaseAuth.addAuthStateListener(authStateListener)
    }

    override suspend fun register(displayName: String, email: String, password: String) = runAuthOperation {
        val user = firebaseAuth
            .createUserWithEmailAndPassword(email, password)
            .await()
            .user
            ?: throw AuthRepositoryException(AuthFailure.Unknown)

        val profile = UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()
        user.updateProfile(profile).await()
        publish(user)

        user.sendEmailVerification().await()
        Unit
    }

    override suspend fun signIn(email: String, password: String) = runAuthOperation {
        val user = firebaseAuth
            .signInWithEmailAndPassword(email, password)
            .await()
            .user
            ?: throw AuthRepositoryException(AuthFailure.InvalidCredentials)
        publish(user)
    }

    override suspend fun sendEmailVerification() = runAuthOperation {
        requireCurrentUser().sendEmailVerification().await()
        Unit
    }

    override suspend fun refreshCurrentUser(): AuthUser = runAuthOperation {
        try {
            requireCurrentUser().reload().await()
            val user = requireCurrentUser()
            user.getIdToken(true).await()
            publish(user)
            user.toAuthUser()
        } catch (error: Throwable) {
            if (error.isExpiredSession()) {
                firebaseAuth.signOut()
                mutableCurrentUser.value = null
                throw AuthRepositoryException(AuthFailure.SessionExpired, error)
            }
            throw error
        }
    }

    override suspend fun sendPasswordReset(email: String) {
        try {
            firebaseAuth.sendPasswordResetEmail(email).await()
        } catch (_: FirebaseAuthInvalidUserException) {
            // The visible response must not reveal whether the account exists.
        } catch (error: Throwable) {
            throw error.toRepositoryException()
        }
    }

    override suspend fun updateDisplayName(displayName: String) = runAuthOperation {
        val user = requireCurrentUser()
        val profile = UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()
        user.updateProfile(profile).await()
        publish(user)
    }

    override fun signOut() {
        firebaseAuth.signOut()
        mutableCurrentUser.value = null
    }

    private fun requireCurrentUser(): FirebaseUser = firebaseAuth.currentUser
        ?: throw AuthRepositoryException(AuthFailure.NoAuthenticatedUser)

    private fun publish(user: FirebaseUser?) {
        mutableCurrentUser.value = user?.toAuthUser()
    }
}

private suspend inline fun <T> runAuthOperation(crossinline operation: suspend () -> T): T = try {
    operation()
} catch (error: AuthRepositoryException) {
    throw error
} catch (error: Throwable) {
    throw error.toRepositoryException()
}

private fun Throwable.toRepositoryException(): AuthRepositoryException = AuthRepositoryException(
    failure = when (this) {
        is FirebaseAuthUserCollisionException -> AuthFailure.EmailAlreadyInUse
        is FirebaseAuthWeakPasswordException -> AuthFailure.WeakPassword
        is FirebaseAuthInvalidCredentialsException,
        is FirebaseAuthInvalidUserException,
        -> AuthFailure.InvalidCredentials
        is FirebaseNetworkException -> AuthFailure.Network
        is FirebaseTooManyRequestsException -> AuthFailure.TooManyRequests
        else -> AuthFailure.Unknown
    },
    cause = this,
)

private fun Throwable.isExpiredSession(): Boolean = (this as? FirebaseAuthInvalidUserException)?.errorCode in setOf(
    "ERROR_USER_TOKEN_EXPIRED",
    "ERROR_USER_DISABLED",
    "ERROR_USER_NOT_FOUND",
) ||
    message?.contains("INVALID_REFRESH_TOKEN", ignoreCase = true) == true

private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
    id = uid,
    displayName = displayName.orEmpty(),
    email = email.orEmpty(),
    isEmailVerified = isEmailVerified,
)
