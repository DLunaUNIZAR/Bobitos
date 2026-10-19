package com.dlunaunizar.bobitos.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private data class TestState(val save: SaveState<String, String> = SaveState(), val extra: String = "")

@OptIn(ExperimentalCoroutinesApi::class)
class SaveRunnerTest {
    private val testScope = TestScope()
    private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScope.testScheduler))
    private val state = MutableStateFlow(TestState())
    private val runner = SaveRunner<TestState, String, String>(
        scope = scope,
        state = state,
        read = TestState::save,
        write = { copy(save = it) },
        toError = { it::class.simpleName ?: "?" },
    )
    private val save get() = state.value.save

    private fun runCurrent() = testScope.runCurrent()

    @Test
    fun `run keeps saving until the action ends and then shows its notice`() {
        val gate = CompletableDeferred<Unit>()

        runner.run(notice = "ok") { gate.await() }
        runCurrent()
        assertTrue(save.isSaving)

        gate.complete(Unit)
        runCurrent()

        assertEquals(0, save.running)
        assertEquals("ok", save.notice)
    }

    @Test
    fun `an action that starts does not clear the notice nor the error pending from another`() {
        val gate = CompletableDeferred<Unit>()
        state.value = TestState(SaveState(error = "e", notice = "n"))

        runner.run<Unit> { gate.await() }
        runCurrent()

        assertEquals("e", save.error)
        assertEquals("n", save.notice)
    }

    @Test
    fun `an action that fails does not clear the notice pending from another`() {
        state.value = TestState(SaveState(notice = "n"))

        runner.run<Unit> { error("fallo") }
        runCurrent()

        assertEquals("n", save.notice)
        assertEquals("IllegalStateException", save.error)
    }

    @Test
    fun `an action that ends without notice leaves the notice pending from another`() {
        state.value = TestState(SaveState(notice = "n"))

        runner.run<Unit> { }
        runCurrent()

        assertEquals("n", save.notice)
    }

    @Test
    fun `an action that ends with its own notice replaces the pending one`() {
        state.value = TestState(SaveState(notice = "n"))

        runner.run(notice = "propio") { }
        runCurrent()

        assertEquals("propio", save.notice)
    }

    @Test
    fun `a second action does not start while another is in flight`() {
        val gate = CompletableDeferred<Unit>()
        var ran = false
        runner.run<Unit> { gate.await() }
        runCurrent()

        val started = runner.run<Unit> { ran = true }
        runCurrent()

        assertFalse(started)
        assertFalse(ran)
    }

    @Test
    fun `a forced action starts while another is in flight and saving stays on until both end`() {
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        runner.run<Unit> { first.await() }
        runner.run<Unit>(force = true) { second.await() }
        runCurrent()
        assertEquals(2, save.running)

        second.complete(Unit)
        runCurrent()
        assertTrue(save.isSaving)

        first.complete(Unit)
        runCurrent()
        assertFalse(save.isSaving)
    }

    @Test
    fun `a forced action ending does not clear a pending created`() {
        runner.create { CreatedItem("1", "Pan") }
        runCurrent()
        assertEquals(CreatedItem("1", "Pan"), save.created)

        runner.run<Unit>(force = true) { }
        runCurrent()

        assertEquals(CreatedItem("1", "Pan"), save.created)
    }

    @Test
    fun `an editor failure goes to the editor error and not to the screen error`() {
        runner.run<Unit>(editor = true) { error("fallo") }
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, save.editor)
        assertEquals("IllegalStateException", save.editorError)
        assertNull(save.error)
    }

    @Test
    fun `a screen failure goes to the screen error even with an editor failure pending`() {
        runner.run<Unit>(editor = true) { error("fallo") }
        runCurrent()

        runner.run<Unit> { throw UnsupportedOperationException() }
        runCurrent()

        assertEquals("UnsupportedOperationException", save.error)
        assertEquals("IllegalStateException", save.editorError)
        assertEquals(EditorSaveStatus.FAILED, save.editor)
    }

    @Test
    fun `showEditorError does not touch saving nor the screen feedback`() {
        val gate = CompletableDeferred<Unit>()
        runner.run<Unit> { gate.await() }
        runner.showNotice("pendiente")
        runCurrent()

        runner.showEditorError("dentro")

        assertTrue(save.isSaving)
        assertEquals("pendiente", save.notice)
        assertNull(save.error)
        assertEquals(EditorSaveStatus.FAILED, save.editor)
        assertEquals("dentro", save.editorError)
    }

    @Test
    fun `consumeError keeps a pending notice and consumeNotice keeps a pending error`() {
        state.value = TestState(SaveState(error = "e", notice = "n"))

        runner.consumeError()
        assertNull(save.error)
        assertEquals("n", save.notice)

        state.value = TestState(SaveState(error = "e", notice = "n"))
        runner.consumeNotice()
        assertNull(save.notice)
        assertEquals("e", save.error)
    }

    @Test
    fun `resetEditor discards only the editor error`() {
        state.value = TestState(
            SaveState(editor = EditorSaveStatus.FAILED, editorError = "x", error = "e", notice = "n"),
        )

        runner.resetEditor()

        assertEquals(EditorSaveStatus.IDLE, save.editor)
        assertNull(save.editorError)
        assertEquals("e", save.error)
        assertEquals("n", save.notice)
    }

    @Test
    fun `releaseEditorError moves a failed editor error to the screen error`() {
        state.value = TestState(SaveState(editor = EditorSaveStatus.FAILED, editorError = "x"))

        runner.releaseEditorError()

        assertEquals(EditorSaveStatus.IDLE, save.editor)
        assertNull(save.editorError)
        assertEquals("x", save.error)
    }

    @Test
    fun `an editor action that never answers fails with the timeout after EDITOR_SAVE_TIMEOUT_MILLIS`() =
        testScope.runTest {
            runner.run<Unit>(editor = true) { awaitCancellation() }
            runCurrent()
            assertEquals(EditorSaveStatus.SAVING, save.editor)

            advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
            runCurrent()

            assertEquals("SaveTimeoutException", save.editorError)
            assertEquals(EditorSaveStatus.FAILED, save.editor)
            assertEquals(0, save.running)
        }

    @Test
    fun `a CancellationException thrown by the action is a normal failure`() {
        runner.run<Unit>(editor = true) { throw CancellationException("del repositorio") }
        runCurrent()

        assertEquals(0, save.running)
        assertEquals("CancellationException", save.editorError)
    }

    @Test
    fun `cancelling the scope ends the action without an error`() {
        runner.run<Unit> { awaitCancellation() }
        runCurrent()
        assertEquals(1, save.running)

        scope.cancel()
        runCurrent()

        assertEquals(0, save.running)
        assertNull(save.error)
    }

    @Test
    fun `launchQuiet shows the notice of the result without turning on saving`() {
        val gate = CompletableDeferred<Int>()

        runner.launchQuiet(noticeOf = { "res=$it" }) { gate.await() }
        runCurrent()
        assertFalse(save.isSaving)

        gate.complete(7)
        runCurrent()

        assertEquals("res=7", save.notice)
        assertNull(save.error)
        assertEquals(0, save.running)
    }

    @Test
    fun `launchQuiet without notice leaves the pending notice and shows a failure translated`() {
        state.value = TestState(SaveState(notice = "n"))

        runner.launchQuiet<Unit> { error("fallo") }
        runCurrent()

        assertEquals("n", save.notice)
        assertEquals("IllegalStateException", save.error)
        assertEquals(0, save.running)
    }

    @Test
    fun `launchQuiet treats a CancellationException from the action as a normal failure`() {
        runner.launchQuiet<Unit> { throw CancellationException("del repositorio") }
        runCurrent()

        assertEquals("CancellationException", save.error)
    }

    @Test
    fun `launchQuiet ends without an error when the scope is cancelled`() {
        runner.launchQuiet<Unit> { awaitCancellation() }
        runCurrent()

        scope.cancel()
        runCurrent()

        assertNull(save.error)
        assertNull(save.notice)
    }

    @Test
    fun `onSuccess and onFailure change the screen state with the result`() {
        runner.run(onSuccess = { copy(extra = "res=$it") }) { 5 }
        runCurrent()
        assertEquals("res=5", state.value.extra)

        runner.run<Int>(onFailure = { copy(extra = "fallo=${it.message}") }) { error("boom") }
        runCurrent()
        assertEquals("fallo=boom", state.value.extra)
    }

    @Test
    fun `afterSuccess sees the state already updated`() {
        var seen: SaveState<String, String>? = null

        runner.run(notice = "ok", afterSuccess = { _: Int -> seen = save }) { 1 }
        runCurrent()

        assertEquals(0, seen?.running)
        assertEquals("ok", seen?.notice)
    }
}
