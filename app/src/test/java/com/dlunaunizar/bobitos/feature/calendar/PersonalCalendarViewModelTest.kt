package com.dlunaunizar.bobitos.feature.calendar

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.SaveTimeoutException
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.EventInput
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant

class PersonalCalendarViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = PersonalRecordingCalendarRepository()
    private val viewModel = PersonalCalendarViewModel(repository, personalEmptyFlows<SpaceRepository>())

    @Test
    fun `editor save is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.createGate = gate

        viewModel.saveEvent("space-1", null, input)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        assertTrue(viewModel.uiState.value.saving)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
        assertFalse(viewModel.uiState.value.saving)
        viewModel.consumeEditorSave()
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
    }

    @Test
    fun `editor save failure or timeout leaves FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.failure = IllegalStateException("sin permiso")
        viewModel.saveEvent("space-1", null, input)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals("sin permiso", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.saving)

        repository.failure = null
        viewModel.consumeEditorSave()
        repository.hang = true
        viewModel.saveEvent("space-1", "event-1", input)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(SaveTimeoutException().message, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.saving)
    }

    @Test
    fun `deleting does not touch the editor status`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.deleteEvent("space-1", "event-1")
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
        assertFalse(viewModel.uiState.value.saving)
    }

    private val input = EventInput(
        title = "Correr",
        description = null,
        allDay = false,
        startAt = Instant.parse("2026-10-09T08:00:00Z"),
        endAt = Instant.parse("2026-10-09T09:00:00Z"),
        startDate = null,
        endDateExclusive = null,
        timeZone = "Europe/Madrid",
        color = EventColor.BLUE,
        participantIds = emptyList(),
    )
}

private class PersonalRecordingCalendarRepository : CalendarRepository {
    var createGate: CompletableDeferred<Unit>? = null
    var failure: Throwable? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    override fun events(spaceId: String, rangeStart: Instant, rangeEndExclusive: Instant): Flow<List<CalendarEvent>> =
        emptyFlow()

    override suspend fun createEvent(spaceId: String, input: EventInput, eventId: String?) {
        failure?.let { throw it }
        awaitIfHanging()
        createGate?.await()
    }

    override suspend fun updateEvent(spaceId: String, eventId: String, input: EventInput) {
        failure?.let { throw it }
        awaitIfHanging()
    }

    override suspend fun deleteEvent(spaceId: String, eventId: String) = Unit

    private suspend fun awaitIfHanging() {
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                error("cancelado")
            }
        }
    }
}

// Repositorios que el test no ejercita: cualquier flujo que se pida está vacío.
private inline fun <reified T> personalEmptyFlows(): T = Proxy.newProxyInstance(
    T::class.java.classLoader,
    arrayOf(T::class.java),
) { proxy, method, args ->
    when {
        Flow::class.java.isAssignableFrom(method.returnType) -> emptyFlow<Any>()
        method.name == "toString" -> T::class.java.simpleName
        method.name == "hashCode" -> System.identityHashCode(proxy)
        method.name == "equals" -> proxy === args?.firstOrNull()
        else -> error("${method.name} no usado")
    }
} as T
