package com.dlunaunizar.bobitos.feature.calendar

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.FailureKind
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.EventInput
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

class PersonalCalendarViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = PersonalRecordingCalendarRepository()
    private val viewModel by lazy { PersonalCalendarViewModel(repository, personalEmptyFlows<SpaceRepository>()) }

    @Test
    fun `editor save is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.createGate = gate

        viewModel.saveEvent("space-1", null, input)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.save.editor)
        assertTrue(viewModel.uiState.value.save.isSaving)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.save.editor)
        assertFalse(viewModel.uiState.value.save.isSaving)
        viewModel.feedback.resetEditor()
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.save.editor)
    }

    @Test
    fun aLoadFailureNeverExposesTheExceptionText() = runTest(mainDispatcherRule.testDispatcher) {
        repository.loadFailure = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")

        viewModel.observe("ana", listOf(SpaceSummary("home", "Casa", 2, SpaceRole.OWNER)))
        advanceUntilIdle()

        assertEquals(UiState.Error(FailureKind.UNKNOWN), viewModel.uiState.value.events)
    }

    @Test
    fun aFailedLoadLeavesEventsInError() = runTest(mainDispatcherRule.testDispatcher) {
        repository.failingLoads = 1

        viewModel.observe("ana", listOf(SpaceSummary("home", "Casa", 2, SpaceRole.OWNER)))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.events is UiState.Error)
    }

    @Test
    fun retryingResubscribesAndCanSucceed() = runTest(mainDispatcherRule.testDispatcher) {
        repository.failingLoads = 1
        repository.served = listOf(sampleEvent().copy(participantIds = listOf("ana")))
        viewModel.observe("ana", listOf(SpaceSummary("home", "Casa", 2, SpaceRole.OWNER)))
        advanceUntilIdle()

        viewModel.retryEvents()
        advanceUntilIdle()

        val events = viewModel.uiState.value.events
        assertTrue(events is UiState.Content)
        assertEquals(1, (events as UiState.Content).value.size)
    }

    @Test
    fun retryingWhileItStillFailsShowsTheErrorAgain() = runTest(mainDispatcherRule.testDispatcher) {
        repository.failingLoads = 2
        viewModel.observe("ana", listOf(SpaceSummary("home", "Casa", 2, SpaceRole.OWNER)))
        advanceUntilIdle()

        viewModel.retryEvents()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.events is UiState.Error)
    }

    private fun space(id: String) = SpaceSummary(id, id.replaceFirstChar(Char::uppercase), 2, SpaceRole.OWNER)

    @Test
    fun desmarcarAUnicoEspacioElegidoDejaTodos() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("ana", listOf(space("casa"), space("trabajo")))
        advanceUntilIdle()

        viewModel.toggleSpace("casa")
        assertEquals(setOf("trabajo"), viewModel.uiState.value.selectedSpaceIds)
        viewModel.toggleSpace("trabajo")

        assertEquals(setOf("casa", "trabajo"), viewModel.uiState.value.selectedSpaceIds)
    }

    @Test
    fun desdeTodosTocarUnEspacioDaLoMismoSeLleguePorDondeSeLlegue() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("ana", listOf(space("casa"), space("trabajo")))
        advanceUntilIdle()

        viewModel.toggleSpace("casa")
        val desdeElInicio = viewModel.uiState.value.selectedSpaceIds
        viewModel.selectAllSpaces()
        viewModel.toggleSpace("casa")
        val desdeElegirTodos = viewModel.uiState.value.selectedSpaceIds
        viewModel.toggleSpace("trabajo")
        viewModel.toggleSpace("casa")
        val desdeDesmarcarElUltimo = viewModel.uiState.value.selectedSpaceIds

        assertEquals(setOf("trabajo"), desdeElInicio)
        assertEquals(desdeElInicio, desdeElegirTodos)
        assertEquals(desdeElInicio, desdeDesmarcarElUltimo)
    }

    @Test
    fun unEspacioNuevoConTodosSigueEnTodos() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("ana", listOf(space("casa"), space("trabajo")))
        advanceUntilIdle()
        viewModel.toggleSpace("casa")
        viewModel.toggleSpace("trabajo")

        viewModel.observe("ana", listOf(space("casa"), space("trabajo"), space("gimnasio")))
        advanceUntilIdle()

        assertEquals(setOf("casa", "trabajo", "gimnasio"), viewModel.uiState.value.selectedSpaceIds)
    }

    @Test
    fun creatingOneItemOffersUndoWithItsIdAndName() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.saveEvent("space-1", null, input)
        advanceUntilIdle()

        assertEquals(CreatedItem("new-1", "Correr"), viewModel.uiState.value.save.created)

        viewModel.feedback.consumeCreated()
        assertNull(viewModel.uiState.value.save.created)
    }

    @Test
    fun undoCreateDeletesExactlyThatItem() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.saveEvent("space-1", null, input)
        advanceUntilIdle()
        viewModel.undoCreate("new-1")
        advanceUntilIdle()

        assertEquals(listOf("new-1"), repository.deleted)
        assertEquals(listOf("space-1"), repository.deletedSpaces)
    }

    @Test
    fun editingDoesNotOfferUndo() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.saveEvent("space-1", "event-1", input)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.save.created)
    }

    @Test
    fun `editor save failure or timeout leaves FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.failure = IllegalStateException("sin permiso")
        viewModel.saveEvent("space-1", null, input)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        assertEquals(FailureKind.UNKNOWN, viewModel.uiState.value.save.editorError)
        assertFalse(viewModel.uiState.value.save.isSaving)

        repository.failure = null
        viewModel.feedback.resetEditor()
        repository.hang = true
        viewModel.saveEvent("space-1", "event-1", input)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.save.editor)
        advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        assertEquals(FailureKind.TIMEOUT, viewModel.uiState.value.save.editorError)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `deleting does not touch the editor status`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.deleteEvent("space-1", "event-1")
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.save.editor)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `undoing a delete recreates the event with its original id in its space`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.restore("space-a", "event-9", input)
            advanceUntilIdle()

            assertEquals(listOf(Triple("space-a", "event-9", input)), repository.created)
            assertNull(viewModel.uiState.value.save.created)
            assertNull(viewModel.uiState.value.save.error)
        }

    @Test
    fun `undo runs even while another write is in flight`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.deleteGate = gate

        viewModel.deleteEvent("space-1", "event-2")
        runCurrent()
        assertTrue(viewModel.uiState.value.save.isSaving)

        viewModel.restore("space-a", "event-1", input)
        runCurrent()

        assertEquals(listOf(Triple("space-a", "event-1", input)), repository.created)
        // El deshacer ya acabó, pero el borrado sigue en curso: «Guardando…» sigue hasta el final.
        assertTrue(viewModel.uiState.value.save.isSaving)
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `a failed undo surfaces the error`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.failure = IllegalStateException("sin permiso")

        viewModel.restore("space-a", "event-9", input)
        advanceUntilIdle()

        assertEquals(FailureKind.UNKNOWN, viewModel.uiState.value.save.error)
    }

    @Test
    fun `undo is offered only once the delete has finished`() = runTest(mainDispatcherRule.testDispatcher) {
        var offered = 0
        repository.deleteFailure = IllegalStateException("sin red")
        viewModel.deleteEvent("space-1", "event-1") { offered++ }
        advanceUntilIdle()
        assertEquals(0, offered)
        assertEquals(FailureKind.UNKNOWN, viewModel.uiState.value.save.error)

        repository.deleteFailure = null
        viewModel.deleteEvent("space-1", "event-1") { offered++ }
        advanceUntilIdle()
        assertEquals(1, offered)
        assertEquals(listOf("event-1"), repository.deleted)
    }

    @Test
    fun cambiarDePeriodoConContenidoNoVuelveACargando() = runTest(mainDispatcherRule.testDispatcher) {
        repository.served = listOf(sampleEvent().copy(participantIds = listOf("ana")))
        viewModel.observe("ana", listOf(SpaceSummary("home", "Casa", 2, SpaceRole.OWNER)))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.refreshing)
        val next = MutableSharedFlow<List<CalendarEvent>>()
        repository.source = next

        viewModel.next()
        runCurrent()

        assertTrue(viewModel.uiState.value.events is UiState.Content)
        assertTrue(viewModel.uiState.value.refreshing)

        next.emit(emptyList())
        advanceUntilIdle()

        assertEquals(UiState.Content(emptyList<PersonalCalendarEvent>()), viewModel.uiState.value.events)
        assertFalse(viewModel.uiState.value.refreshing)
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
    val deleted = mutableListOf<String>()
    val deletedSpaces = mutableListOf<String>()
    val created = mutableListOf<Triple<String, String?, EventInput>>()
    var deleteFailure: Throwable? = null
    var failure: Throwable? = null
    var loadFailure: Throwable? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    // Si hay fuente, las suscripciones nuevas la reciben en vez de `served`.
    var source: Flow<List<CalendarEvent>>? = null

    override fun events(spaceId: String, rangeStart: Instant, rangeEndExclusive: Instant): Flow<List<CalendarEvent>> =
        nextLoadFailure()?.let { error -> flow { throw error } } ?: source ?: served?.let { flowOf(it) } ?: emptyFlow()

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

    var deleteGate: CompletableDeferred<Unit>? = null

    override suspend fun deleteEvent(spaceId: String, eventId: String) {
        deleteGate?.await()
        deleteFailure?.let { throw it }
        deletedSpaces += spaceId
        deleted += eventId
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
