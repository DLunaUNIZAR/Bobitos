package com.dlunaunizar.bobitos.feature.betaupdate

import com.dlunaunizar.bobitos.data.betaupdate.BetaRelease
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BetaUpdatePolicyTest {
    private val today = LocalDate.of(2026, 10, 18)

    private fun release(versionCode: Long) = BetaRelease("0.1.0-beta.$versionCode", versionCode, null)

    private fun check(available: Boolean = true, testerSignedIn: Boolean = true, lastAutoCheck: LocalDate? = null) =
        shouldAutoCheck(available, testerSignedIn, today, lastAutoCheck)

    @Test
    fun `comprueba si hay sesión y hoy no se ha comprobado`() {
        assertEquals(true, check(lastAutoCheck = null))
        assertEquals(true, check(lastAutoCheck = LocalDate.of(2026, 10, 17)))
    }

    @Test
    fun `no comprueba dos veces el mismo día`() {
        assertEquals(false, check(lastAutoCheck = today))
    }

    @Test
    fun `no comprueba sin sesión de tester`() {
        assertEquals(false, check(testerSignedIn = false))
    }

    @Test
    fun `no comprueba en desarrollo`() {
        assertEquals(false, check(available = false))
    }

    @Test
    fun `avisa de una versión nueva sin posponer`() {
        assertEquals(true, shouldPrompt(release(25), null))
    }

    @Test
    fun `no avisa de la versión pospuesta`() {
        assertEquals(false, shouldPrompt(release(25), 25))
    }

    @Test
    fun `avisa de una versión más nueva que la pospuesta`() {
        assertEquals(true, shouldPrompt(release(26), 25))
    }

    @Test
    fun `sin versión no avisa`() {
        assertEquals(false, shouldPrompt(null, 25))
    }
}
