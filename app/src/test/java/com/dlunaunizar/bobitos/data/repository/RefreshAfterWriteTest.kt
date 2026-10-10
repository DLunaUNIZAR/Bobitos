package com.dlunaunizar.bobitos.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshAfterWriteTest {
    @Test
    fun `a refresh that never answers ends the save without adopting the version`() = runTest(timeout = 3.seconds) {
        var adopted = false

        refreshAfterWrite(5_000, refresh = { awaitCancellation() }, adopt = { adopted = true })

        assertEquals(false, adopted)
        assertEquals(5_000L, currentTime)
    }

    @Test
    fun `a refresh that fails ends the save without adopting the version`() = runTest(timeout = 3.seconds) {
        var adopted = false

        refreshAfterWrite(5_000, refresh = { throw IOException("sin red") }, adopt = { adopted = true })

        assertEquals(false, adopted)
    }

    @Test
    fun `a refresh that answers adopts the version`() = runTest(timeout = 3.seconds) {
        var adopted = false

        refreshAfterWrite(5_000, refresh = { true }, adopt = { adopted = true })

        assertEquals(true, adopted)
    }
}
