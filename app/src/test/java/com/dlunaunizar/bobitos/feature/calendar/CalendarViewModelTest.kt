package com.dlunaunizar.bobitos.feature.calendar

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.EventInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        advanceUntilIdle()
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

    override fun events(spaceId: String, rangeStart: Instant, rangeEndExclusive: Instant): Flow<List<CalendarEvent>> =
        emptyFlow()

    override suspend fun createEvent(spaceId: String, input: EventInput, eventId: String?) {
        created += Triple(spaceId, eventId, input)
    }

    override suspend fun updateEvent(spaceId: String, eventId: String, input: EventInput) = Unit

    val pendingDelete = CompletableDeferred<Unit>()

    override suspend fun deleteEvent(spaceId: String, eventId: String) = pendingDelete.await()
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
