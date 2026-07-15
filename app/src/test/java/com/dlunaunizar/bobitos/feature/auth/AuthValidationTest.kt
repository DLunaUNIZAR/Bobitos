package com.dlunaunizar.bobitos.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {
    @Test
    fun `valid registration has no validation error`() {
        val error = AuthValidation.validateRegistration(
            displayName = "David Luna",
            email = "david@example.com",
            password = "password-123",
            passwordConfirmation = "password-123",
        )

        assertNull(error)
    }

    @Test
    fun `registration rejects an invalid email`() {
        val error = AuthValidation.validateRegistration(
            displayName = "David Luna",
            email = "correo-invalido",
            password = "password-123",
            passwordConfirmation = "password-123",
        )

        assertEquals(AuthUiMessage.InvalidEmail, error)
    }

    @Test
    fun `registration requires eight password characters`() {
        val error = AuthValidation.validateRegistration(
            displayName = "David Luna",
            email = "david@example.com",
            password = "1234567",
            passwordConfirmation = "1234567",
        )

        assertEquals(AuthUiMessage.PasswordTooShort, error)
    }

    @Test
    fun `registration requires matching passwords`() {
        val error = AuthValidation.validateRegistration(
            displayName = "David Luna",
            email = "david@example.com",
            password = "password-123",
            passwordConfirmation = "different-password",
        )

        assertEquals(AuthUiMessage.PasswordsDoNotMatch, error)
    }
}
