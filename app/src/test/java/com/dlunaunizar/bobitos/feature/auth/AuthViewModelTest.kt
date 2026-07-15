package com.dlunaunizar.bobitos.feature.auth

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.model.AuthUser
import com.dlunaunizar.bobitos.data.repository.AuthFailure
import com.dlunaunizar.bobitos.data.repository.AuthRepository
import com.dlunaunizar.bobitos.data.repository.AuthRepositoryException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val viewModel = AuthViewModel(repository)

    @Test
    fun `valid registration trims the name and email`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.register(
            displayName = "  David Luna  ",
            email = "  david@example.com  ",
            password = "password-123",
            passwordConfirmation = "password-123",
        )
        advanceUntilIdle()

        assertEquals("David Luna", repository.registeredName)
        assertEquals("david@example.com", repository.registeredEmail)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `invalid registration does not call the repository`() {
        viewModel.register(
            displayName = "David",
            email = "invalid-email",
            password = "password-123",
            passwordConfirmation = "password-123",
        )

        assertNull(repository.registeredEmail)
        assertEquals(AuthUiMessage.InvalidEmail, viewModel.uiState.value.error)
    }

    @Test
    fun `sign in does not expose whether the account exists`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.nextFailure = AuthRepositoryException(AuthFailure.InvalidCredentials)

            viewModel.signIn("missing@example.com", "password-123")
            advanceUntilIdle()

            assertEquals(
                AuthUiMessage.InvalidCredentials,
                viewModel.uiState.value.error,
            )
        }

    @Test
    fun `password reset always shows the neutral confirmation`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.sendPasswordReset("person@example.com")
            advanceUntilIdle()

            assertEquals(
                AuthUiMessage.PasswordResetRequested,
                viewModel.uiState.value.notice,
            )
        }

    @Test
    fun `verification resend is limited to once per minute`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.resendVerificationEmail()
            runCurrent()
            viewModel.resendVerificationEmail()
            runCurrent()

            assertEquals(1, repository.verificationEmailsSent)
            assertEquals(60, viewModel.uiState.value.verificationResendSecondsRemaining)

            advanceTimeBy(60_000)
            runCurrent()
            viewModel.resendVerificationEmail()
            runCurrent()

            assertEquals(2, repository.verificationEmailsSent)
        }
}

private class FakeAuthRepository : AuthRepository {
    override val currentUser: StateFlow<AuthUser?> = MutableStateFlow(null)

    var registeredName: String? = null
    var registeredEmail: String? = null
    var nextFailure: AuthRepositoryException? = null
    var verificationEmailsSent = 0

    override suspend fun register(
        displayName: String,
        email: String,
        password: String,
    ) {
        nextFailure?.let { throw it }
        registeredName = displayName
        registeredEmail = email
    }

    override suspend fun signIn(email: String, password: String) {
        nextFailure?.let { throw it }
    }

    override suspend fun sendEmailVerification() {
        nextFailure?.let { throw it }
        verificationEmailsSent += 1
    }

    override suspend fun refreshCurrentUser(): AuthUser {
        nextFailure?.let { throw it }
        return AuthUser("id", "David", "david@example.com", false)
    }

    override suspend fun sendPasswordReset(email: String) {
        nextFailure?.let { throw it }
    }

    override suspend fun updateDisplayName(displayName: String) {
        nextFailure?.let { throw it }
    }

    override fun signOut() = Unit
}
