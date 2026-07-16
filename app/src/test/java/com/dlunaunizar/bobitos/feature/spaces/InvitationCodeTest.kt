package com.dlunaunizar.bobitos.feature.spaces

import com.dlunaunizar.bobitos.core.model.InvitationCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationCodeTest {
    @Test
    fun `generated token has 160 bits encoded as base32`() {
        val token = InvitationCode.generate()

        assertEquals(32, token.length)
        assertTrue(token.matches(Regex("^[A-Z2-7]{32}$")))
        assertNotEquals(token, InvitationCode.generate())
    }

    @Test
    fun `normalization accepts grouped lowercase code`() {
        val token = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

        assertEquals(token, InvitationCode.normalize("abcd-efgh-ijkl-mnop-qrst-uvwx-yz23-4567"))
    }

    @Test
    fun `invalid code is rejected`() {
        assertNull(InvitationCode.normalize("short-code"))
        assertNull(InvitationCode.normalize("0".repeat(32)))
    }

    @Test
    fun `deep link extracts invitation token`() {
        val token = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

        assertEquals(token, InvitationCode.fromDeepLink("bobitos://invite/$token"))
        assertNull(InvitationCode.fromDeepLink("https://example.com/invite/$token"))
    }
}
