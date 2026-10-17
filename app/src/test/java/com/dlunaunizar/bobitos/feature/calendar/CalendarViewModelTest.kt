package com.dlunaunizar.bobitos.feature.calendar

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.FailureKind
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.EventInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant

class CalendarViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `undoing a delete recreates the event with its original id`() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingCalendarRepository()
        val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())
        viewModel.observe("space-1")

        viewModel.restore("event-1", input)
        advanceUntilIdle()

        assertEquals(listOf(Triple("space-1", "event-1", input)), repository.created)
    }

    @Test
    fun `undo runs even while another write is in flight`() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingCalendarRepository()
        val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())
        viewModel.observe("space-1")

        viewModel.delete("event-2")
        runCurrent()
        assertTrue(viewModel.uiState.value.saving)

        viewModel.restore("event-1", input)
        runCurrent()

        assertEquals(listOf(Triple("space-1", "event-1", input)), repository.created)
    }

    @Test
    fun `saving a new event lets the repository choose the id`() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingCalendarRepository()
        val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())
        viewModel.observe("space-1")

        viewModel.save(null, input)
        advanceUntilIdle()

        assertEquals(listOf(Triple("space-1", null, input)), repository.created)
    }

    @Test
    fun `undo is offered only once the delete has finished`() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingCalendarRepository()
        val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())
        viewModel.observe("space-1")
        var savingWhenOffered: Boolean? = null

        viewModel.delete("event-1") { savingWhenOffered = viewModel.uiState.value.saving }
        runCurrent()
        assertNull(savingWhenOffered)

        repository.pendingDelete.complete(Unit)
        advanceUntilIdle()
        assertEquals(false, savingWhenOffered)
    }

    @Test
    fun `undo is not offered when the delete fails`() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingCalendarRepository()
        val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())
        viewModel.observe("space-1")
        var offered = false

        viewModel.delete("event-1") { offered = true }
        repository.pendingDelete.completeExceptionally(IllegalStateException("ya borrado"))
        advanceUntilIdle()

        assertFalse(offered)
    }

    @Test
    fun `editor save is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingCalendarRepository()
        val gate = CompletableDeferred<Unit>()
        repository.createGate = gate
        val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())
        viewModel.observe("space-1")

        viewModel.save(null, input)
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
    fun aLoadFailureNeverExposesTheExceptionText() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingCalendarRepository()
        repository.loadFailure = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")
        val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())

        viewModel.observe("space-1")
        advanceUntilIdle()

        assertEquals(UiState.Error(FailureKind.UNKNOWN), viewModel.uiState.value.events)
    }

    @Test
    fun `a successful action leaves the saved notice and no error`() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingCalendarRepository()
        val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())
        viewModel.observe("space-1")

        viewModel.save("event-1", input)
        advanceUntilIdle()

        assertEquals(CalendarNotice.SAVED, viewModel.uiState.value.notice)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun aFailedLoadLeavesEventsInError() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingCalendarRepository()
        val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())
        repository.failingLoads = 1

        viewModel.observe("space-1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.events is UiState.Error)
    }

    @Test
    fun retryingResubscribesAndCanSucceed() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingCalendarRepository()
        val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())
        repository.failingLoads = 1
        repository.served = listOf(sampleEvent())
        viewModel.observe("space-1")
        advanceUntilIdle()

        viewModel.retryEvents()
        advanceUntilIdle()

        val events = viewModel.uiState.value.events
        assertTrue(events is UiState.Content)
        assertEquals(1, (events as UiState.Content).value.size)
    }

    @Test
    fun retryingWhileItStillFailsShowsTheErrorAgain() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingCalendarRepository()
        val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())
        repository.failingLoads = 2
        viewModel.observe("space-1")
        advanceUntilIdle()

        viewModel.retryEvents()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.events is UiState.Error)
    }

    @Test
    fun creatingOneItemOffersUndoWithItsIdAndName() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingCalendarRepository()
        val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())
        viewModel.observe("space-1")

        viewModel.save(null, input)
        advanceUntilIdle()

        assertEquals(CreatedItem("new-1", "Correr"), viewModel.uiState.value.created)
        assertNull(viewModel.uiState.value.notice)

        viewModel.consumeCreated()
        assertNull(viewModel.uiState.value.created)
    }

    @Test
    fun undoCreateDeletesExactlyThatItem() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingCalendarRepository()
        repository.pendingDelete.complete(Unit)
        val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())
        viewModel.observe("space-1")

        viewModel.undoCreate("new-1")
        advanceUntilIdle()

        assertEquals(listOf("new-1"), repository.deleted)
    }

    @Test
    fun editingDoesNotOfferUndo() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingCalendarRepository()
        val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())
        viewModel.observe("space-1")

        viewModel.save("event-1", input)
        advanceUntilIdle()
        viewModel.restore("event-2", input)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.created)
    }

    @Test
    fun `deleting leaves no notice because the undo snackbar already confirms it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = RecordingCalendarRepository()
            val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())
            viewModel.observe("space-1")
            var deleted = false

            viewModel.delete("event-1") { deleted = true }
            repository.pendingDelete.complete(Unit)
            advanceUntilIdle()

            assertEquals(true, deleted)
            assertNull(viewModel.uiState.value.notice)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `editor save failure or timeout leaves FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingCalendarRepository()
        val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())
        viewModel.observe("space-1")
        repository.failure = IllegalStateException("sin permiso")
        viewModel.save(null, input)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(FailureKind.UNKNOWN, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.saving)

        repository.failure = null
        viewModel.consumeEditorSave()
        repository.hang = true
        viewModel.save("event-1", input)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(FailureKind.TIMEOUT, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.saving)
    }

    @Test
    fun `restore and delete never touch the editor status`() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingCalendarRepository()
        val viewModel = CalendarViewModel(repository, emptyFlows(), emptyFlows())
        viewModel.observe("space-1")

        viewModel.restore("event-1", input)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)

        viewModel.delete("event-1")
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
        repository.pendingDelete.complete(Unit)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
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

private class RecordingCalendarRepository : CalendarRepository {
    val created = mutableListOf<Triple<String, String?, EventInput>>()
    val deleted = mutableListOf<String>()
    var createGate: CompletableDeferred<Unit>? = null
    var failure: Throwable? = null
    var loadFailure: Throwable? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    override fun events(spaceId: String, rangeStart: Instant, rangeEndExclusive: Instant): Flow<List<CalendarEvent>> =
        nextLoadFailure()?.let { error -> flow { throw error } } ?: served?.let { flowOf(it) } ?: emptyFlow()

    // Los primeros `failingLoads` abrir la suscripción fallan; después, emite `served`.
    var failingLoads = 0
    var served: List<CalendarEvent>? = null

    private fun nextLoadFailure(): Throwable? = loadFailure ?: if (failingLoads > 0) {
        failingLoads--
        RuntimeException("sin conexión")
    } else {
        null
    }

    override suspend fun createEvent(spaceId: String, input: EventInput, eventId: String?): String {
        failure?.let { throw it }
        awaitIfHanging()
        createGate?.await()
        created += Triple(spaceId, eventId, input)
        return eventId ?: "new-1"
    }

    override suspend fun updateEvent(spaceId: String, eventId: String, input: EventInput) {
        failure?.let { throw it }
        awaitIfHanging()
    }

    private suspend fun awaitIfHanging() {
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                error("cancelado")
            }
        }
    }

    val pendingDelete = CompletableDeferred<Unit>()

    override suspend fun deleteEvent(spaceId: String, eventId: String) {
        deleted += eventId
        pendingDelete.await()
    }
}

// Repositorios que el test no ejercita: cualquier flujo que se pida está vacío.
private inline fun <reified T> emptyFlows(): T = Proxy.newProxyInstance(
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

private fun sampleEvent() = CalendarEvent(
    id = "event-1",
    title = "Cena",
    description = null,
    allDay = false,
    startAt = Instant.parse("2026-10-09T18:00:00Z"),
    endAt = Instant.parse("2026-10-09T19:00:00Z"),
    startDate = null,
    endDateExclusive = null,
    timeZone = "Europe/Madrid",
    color = EventColor.BLUE,
    participantIds = emptyList(),
    participantNames = emptyList(),
    createdBy = "ana",
    createdByName = "Ana",
    createdAt = Instant.parse("2026-10-01T10:00:00Z"),
    updatedBy = "ana",
    updatedAt = Instant.parse("2026-10-01T10:00:00Z"),
)
