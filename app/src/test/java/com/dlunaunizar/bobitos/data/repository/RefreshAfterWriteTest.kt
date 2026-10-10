package com.dlunaunizar.bobitos.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
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

    @Test
    fun `the save ends as soon as the commit answers even if the refresh never does`() = runTest(timeout = 3.seconds) {
        var done = false
        var adopted: Long? = null

        commitThenRefresh(
            scope = backgroundScope,
            timeoutMillis = 5_000,
            commit = { 7L },
            refresh = { awaitCancellation() },
            adopt = { adopted = it },
            onDone = { done = true },
        )

        assertEquals(0L, currentTime)
        assertEquals(false, done)

        advanceTimeBy(5_001)
        runCurrent()

        assertEquals(true, done)
        assertEquals(null, adopted)
    }

    @Test
    fun `a failing adopt never fails the save and still notifies`() = runTest(timeout = 3.seconds) {
        var done = false

        commitThenRefresh(
            scope = backgroundScope,
            timeoutMillis = 5_000,
            commit = { 7L },
            refresh = { true },
            adopt = { throw IOException("disco lleno") },
            onDone = { done = true },
        )
        runCurrent()

        assertEquals(true, done)
    }
}
