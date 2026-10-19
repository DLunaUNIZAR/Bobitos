package com.dlunaunizar.bobitos.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PerIdWriteTest {
    private val testScope = TestScope()
    private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScope.testScheduler))
    private val ids = mutableSetOf<String>()

    @Test
    fun `a CancellationException thrown by the block calls onFailure and frees the id`() {
        val failure = CancellationException("del repositorio")
        var received: Throwable? = null
        var finished: Boolean? = null

        scope.launchPerIdWrite(ids, "a", onFinished = { finished = it }, onFailure = { received = it }) {
            throw failure
        }
        testScope.runCurrent()

        assertEquals(failure, received)
        assertEquals(false, finished)
        assertFalse("a" in ids)
    }

    @Test
    fun `cancelling the scope frees the id without onFailure`() {
        var failed = false
        var finished: Boolean? = null
        assertTrue(
            scope.launchPerIdWrite(ids, "a", onFinished = { finished = it }, onFailure = { failed = true }) {
                awaitCancellation()
            },
        )
        testScope.runCurrent()
        assertTrue("a" in ids)

        scope.cancel()
        testScope.runCurrent()

        assertFalse("a" in ids)
        assertFalse(failed)
        assertEquals(false, finished)
    }
}
