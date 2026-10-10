package com.dlunaunizar.bobitos.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class EditorSaveTest {
    @Test
    fun `returns the block result in time`() = runTest {
        assertEquals(7, withSaveTimeout(1_000) { 7 })
    }

    @Test
    fun `throws SaveTimeoutException when the block never answers`() = runTest {
        val result = runCatching { withSaveTimeout(1_000) { delay(5_000) } }

        assertTrue(result.exceptionOrNull() is SaveTimeoutException)
    }

    @Test
    fun `detects the timeout even if the block turns the cancellation into another exception`() = runTest {
        val result = runCatching {
            withSaveTimeout(1_000) {
                try {
                    awaitCancellation()
                } catch (_: CancellationException) {
                    error("el repositorio convierte la cancelación")
                }
            }
        }

        assertTrue(result.exceptionOrNull() is SaveTimeoutException)
    }

    @Test
    fun `rethrows the block failure unchanged`() = runTest {
        val failure = IllegalStateException("fallo")

        try {
            withSaveTimeout(1_000) { throw failure }
            fail("debía lanzar")
        } catch (error: IllegalStateException) {
            assertSame(failure, error)
        }
    }

    @Test
    fun `outer cancellation is not reported as a timeout`() = runTest {
        var result: Result<Unit>? = null
        val job = launch {
            result = runCatching { withSaveTimeout(10_000) { awaitCancellation() } }
        }
        runCurrent()

        job.cancel()
        job.join()

        val error = result?.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertTrue(error !is SaveTimeoutException)
    }

    @Test
    fun `status only moves for editor saves`() {
        assertEquals(EditorSaveStatus.SAVING, EditorSaveStatus.IDLE.started(true))
        assertEquals(EditorSaveStatus.IDLE, EditorSaveStatus.IDLE.started(false))
        assertEquals(EditorSaveStatus.SAVED, EditorSaveStatus.SAVING.succeeded(true))
        assertEquals(EditorSaveStatus.SAVING, EditorSaveStatus.SAVING.succeeded(false))
        assertEquals(EditorSaveStatus.FAILED, EditorSaveStatus.SAVING.failed(true))
        assertEquals(EditorSaveStatus.SAVING, EditorSaveStatus.SAVING.failed(false))
        assertEquals(EditorSaveStatus.IDLE, EditorSaveStatus.IDLE.failed(false))
    }

    @Test
    fun `timeout message matches write_timeout in strings xml`() {
        val xml = File("src/main/res/values/strings.xml").readText()
        val text = Regex("""<string name="write_timeout">(.*?)</string>""").find(xml)!!.groupValues[1]

        assertEquals(text, SaveTimeoutException().message)
    }
}
