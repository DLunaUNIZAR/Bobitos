package com.dlunaunizar.bobitos.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthUserTest {
    @Test
    fun `initials use the first two words of the display name`() {
        val user = AuthUser(
            id = "user-id",
            displayName = "David Luna",
            email = "david@example.com",
            isEmailVerified = true,
        )

        assertEquals("DL", user.initials)
    }

    @Test
    fun `initials fall back to the email when the name is empty`() {
        val user = AuthUser(
            id = "user-id",
            displayName = "",
            email = "bobitos@example.com",
            isEmailVerified = false,
        )

        assertEquals("BO", user.initials)
    }
}
