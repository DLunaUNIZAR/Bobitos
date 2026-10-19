package com.dlunaunizar.bobitos.feature.tasks

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.FailureKind
import com.dlunaunizar.bobitos.core.common.UNDO_OFFER_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.Undoable
import com.dlunaunizar.bobitos.core.model.RecurrenceUnit
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskRecurrence
import com.dlunaunizar.bobitos.core.model.TaskStatus
import com.dlunaunizar.bobitos.core.model.TaskType
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.TaskDates
import com.dlunaunizar.bobitos.data.repository.TaskFailure
import com.dlunaunizar.bobitos.data.repository.TaskRepository
import com.dlunaunizar.bobitos.data.repository.TaskRepositoryException
import com.dlunaunizar.bobitos.data.repository.completedWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val taskRepository = FakeTaskRepository()
    private val spaceRepository = FakeSpaceRepository()
    private val viewModel by lazy { TasksViewModel(taskRepository, spaceRepository) }

    @Test
    fun `observes tasks and members for the active space`() = runTest(mainDispatcherRule.testDispatcher) {
        spaceRepository.membersState.value = listOf(SpaceMember("ana", "Ana", SpaceRole.MEMBER))
        taskRepository.tasksState.value = listOf(task("t1", "Fregar", TaskType.LIMPIEZA))

        viewModel.observe("home")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("home", taskRepository.observedSpaceId)
        assertEquals(listOf("Fregar"), (state.tasks as UiState.Content).value.map(TaskItem::title))
        assertEquals(listOf("Ana"), (state.members as UiState.Content).value.map(SpaceMember::displayName))
    }

    @Test
    fun `creating a task trims the title and reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.createTask(
            "home",
            "  Fregar  ",
            null,
            "ana",
            null,
            TaskPriority.MEDIUM,
            TaskType.LIMPIEZA,
            null,
            null,
        )
        runCurrent()

        assertEquals("Fregar", taskRepository.createdTitle)
        assertEquals(TaskType.LIMPIEZA, taskRepository.createdType)
        assertNull(viewModel.uiState.value.save.notice)
        assertEquals(
            listOf(Undoable.Created("home", CreatedItem("new-1", "Fregar"))),
            viewModel.undoOffers.value.map { it.undoable },
        )
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `creating an unassigned task is allowed and reaches the repository with a null assignee`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.createTask(
                "home",
                "Sacar la basura",
                null,
                null,
                null,
                TaskPriority.MEDIUM,
                null,
                null,
                null,
            )
            advanceUntilIdle()

            assertEquals("Sacar la basura", taskRepository.createdTitle)
            assertTrue(taskRepository.createdAssigneeSet)
            assertNull(taskRepository.createdAssignee)
            assertNull(viewModel.uiState.value.save.notice)
        }

    @Test
    fun `an invalid task never reaches the repository`() {
        viewModel.createTask("home", "   ", null, "ana", null, TaskPriority.LOW, null, null, null)

        assertNull(taskRepository.createdTitle)
        assertEquals(TaskUiMessage.TitleRequired, viewModel.uiState.value.save.editorError)
        assertNull(viewModel.uiState.value.save.error)
    }

    @Test
    fun `marking a task delegates to the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.setCompleted("home", task("t1", "Leche", null), true)
        advanceUntilIdle()

        assertEquals(Triple("home", "t1", true), taskRepository.completedChange)
    }

    @Test
    fun completarTresSeguidasEscribeLasTresAlMomento() = runTest(mainDispatcherRule.testDispatcher) {
        taskRepository.completeGate = CompletableDeferred()

        assertTrue(viewModel.setCompleted("home", task("t1", "Leche", null), true))
        assertTrue(viewModel.setCompleted("home", task("t2", "Pan", null), true))
        assertTrue(viewModel.setCompleted("home", task("t3", "Sal", null), true))
        runCurrent()

        // Las tres escrituras están en curso a la vez, sin esperar a que acabe la primera, y no bloquean el resto.
        assertEquals(listOf("t1", "t2", "t3"), taskRepository.completeStarted)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun completarLaMismaTareaDosVecesMientrasEscribeSoloEscribeUna() = runTest(mainDispatcherRule.testDispatcher) {
        taskRepository.completeGate = CompletableDeferred()

        assertTrue(viewModel.setCompleted("home", task("t1", "Leche", null), true))
        assertFalse(viewModel.setCompleted("home", task("t1", "Leche", null), true))
        runCurrent()

        assertEquals(listOf("t1"), taskRepository.completeStarted)
    }

    @Test
    fun completarAvisaDeSuResultadoYPermiteVolverAIntentarlo() = runTest(mainDispatcherRule.testDispatcher) {
        val resultados = mutableListOf<Boolean>()
        taskRepository.nextFailure = TaskRepositoryException(TaskFailure.Network)

        viewModel.setCompleted("home", task("t1", "Leche", null), true, onFinished = { resultados += it })
        advanceUntilIdle()
        taskRepository.nextFailure = null
        viewModel.setCompleted("home", task("t1", "Leche", null), true, onFinished = { resultados += it })
        advanceUntilIdle()

        assertEquals(listOf(false, true), resultados)
    }

    @Test
    fun `completing offers undo with the dates the write left`() = runTest(mainDispatcherRule.testDispatcher) {
        val start = Instant.parse("2026-10-01T08:00:00Z")
        val due = Instant.parse("2026-10-02T08:00:00Z")
        val dates = TaskDates(start.plusSeconds(604_800), due.plusSeconds(604_800))
        val antes = task("t1", "Regar", null).copy(
            startAt = start,
            dueAt = due,
            recurrence = TaskRecurrence(RecurrenceUnit.WEEK, 1),
        )
        taskRepository.completionDates = dates

        viewModel.setCompleted("home", antes, true)
        runCurrent()

        assertEquals(
            listOf(Undoable.Completed("home", antes, antes.completedWith(dates))),
            viewModel.undoOffers.value.map { it.undoable },
        )
        assertNull(viewModel.uiState.value.save.notice)
    }

    @Test
    fun `completing a task that was already done offers no undo`() = runTest(mainDispatcherRule.testDispatcher) {
        taskRepository.completionDates = null
        val resultados = mutableListOf<Boolean>()

        viewModel.setCompleted("home", task("t1", "Leche", null), true, onFinished = { resultados += it })
        runCurrent()

        assertTrue(viewModel.undoOffers.value.isEmpty())
        assertEquals(listOf(true), resultados)
        assertNull(viewModel.uiState.value.save.error)
    }

    @Test
    fun `reopening a task offers no undo`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.setCompleted("home", task("t1", "Leche", null), false)
        advanceUntilIdle()

        assertTrue(viewModel.undoOffers.value.isEmpty())
    }

    @Test
    fun deshacerDevuelveLaTareaConSuEstadoYSusFechas() = runTest(mainDispatcherRule.testDispatcher) {
        val start = Instant.parse("2026-10-01T08:00:00Z")
        val due = Instant.parse("2026-10-02T08:00:00Z")
        val antes = task("t1", "Regar", null).copy(
            startAt = start,
            dueAt = due,
            recurrence = TaskRecurrence(RecurrenceUnit.WEEK, 1),
        )

        val hecha = antes.completedWith(TaskDates(start, due))

        viewModel.undo(Undoable.Completed("home", antes, hecha))
        advanceUntilIdle()

        assertEquals("home", taskRepository.completionRestoredSpaceId)
        assertEquals(antes, taskRepository.completionRestored.single())
        assertEquals(hecha, taskRepository.completionRestoredTo.single())
        assertNull(taskRepository.completedChange)
    }

    @Test
    fun deshacerSiLaTareaYaNoExisteAvisa() = runTest(mainDispatcherRule.testDispatcher) {
        taskRepository.nextFailure = TaskRepositoryException(TaskFailure.TaskNotFound)

        viewModel.undo(Undoable.Completed("home", task("t1", "Regar", null), task("t1", "Regar", null)))
        advanceUntilIdle()

        assertEquals(TaskUiMessage.TaskNotFound, viewModel.uiState.value.save.error)
    }

    @Test
    fun deshacerUnaTareaQueOtroCambioAvisa() = runTest(mainDispatcherRule.testDispatcher) {
        taskRepository.nextFailure = TaskRepositoryException(TaskFailure.TaskChanged)

        viewModel.undo(Undoable.Completed("home", task("t1", "Regar", null), task("t1", "Regar", null)))
        advanceUntilIdle()

        assertEquals(TaskUiMessage.TaskChanged, viewModel.uiState.value.save.error)
    }

    @Test
    fun aLoadFailureNeverExposesTheExceptionText() = runTest(mainDispatcherRule.testDispatcher) {
        taskRepository.loadFailure = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")

        viewModel.observe("home")
        advanceUntilIdle()

        assertEquals(UiState.Error(FailureKind.UNKNOWN), viewModel.uiState.value.tasks)
    }

    @Test
    fun `network failure is shown explicitly`() = runTest(mainDispatcherRule.testDispatcher) {
        taskRepository.nextFailure = TaskRepositoryException(TaskFailure.Network)

        viewModel.deleteTask("home", task("t1", "Fregar", null))
        advanceUntilIdle()

        assertEquals(TaskUiMessage.NetworkError, viewModel.uiState.value.save.error)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `clearFilters goes back to the default filters`() {
        viewModel.setFilters(
            TaskFilters(
                status = TaskStatus.DONE,
                priority = TaskPriority.HIGH,
                type = TaskType.LIMPIEZA,
                assigneeId = "u2",
            ),
        )

        viewModel.clearFilters()

        assertEquals(TaskFilters(), viewModel.uiState.value.filters)
    }

    @Test
    fun unaEscrituraCanceladaLiberaLaTareaYAvisaDeQueNoTermino() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        taskRepository.completeGate = gate
        val resultados = mutableListOf<Boolean>()
        val tarea = task("t1", "Fregar", TaskType.LIMPIEZA)

        assertTrue(viewModel.setCompleted("home", tarea, true, { resultados += it }))
        runCurrent()
        gate.cancel()
        advanceUntilIdle()

        // La cancelación no cuenta como éxito y la tarea queda libre para el siguiente toque.
        assertEquals(listOf(false), resultados)
        taskRepository.completeGate = null
        assertTrue(viewModel.setCompleted("home", tarea, true, { resultados += it }))
        advanceUntilIdle()
        assertEquals(Triple("home", "t1", true), taskRepository.completedChange)
        assertEquals(listOf(false, true), resultados)
    }

    @Test
    fun `filtering by type keeps only that category`() {
        val tasks = listOf(
            task("t1", "Fregar", TaskType.LIMPIEZA),
            task("t2", "Médico", TaskType.MEDICO),
            task("t3", "Sin tipo", null),
        )

        val filtered = TaskFilters(status = null, type = TaskType.LIMPIEZA).apply(tasks)

        assertEquals(listOf("Fregar"), filtered.map(TaskItem::title))
    }

    @Test
    fun `editor create stays SAVING until the repository answers, then SAVED`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val gate = CompletableDeferred<Unit>()
            taskRepository.createGate = gate

            editorCreate("Fregar")
            assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.save.editor)
            assertTrue(viewModel.uiState.value.save.isSaving)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.save.editor)
            assertFalse(viewModel.uiState.value.save.isSaving)
        }

    @Test
    fun `editor save failure leaves FAILED with the editor error and isSaving false`() =
        runTest(mainDispatcherRule.testDispatcher) {
            taskRepository.nextFailure = TaskRepositoryException(TaskFailure.Network)

            editorCreate("Fregar")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(EditorSaveStatus.FAILED, state.save.editor)
            assertEquals(TaskUiMessage.NetworkError, state.save.editorError)
            assertNull(state.save.error)
            assertFalse(state.save.isSaving)
        }

    @Test
    fun `editor save without answer times out into FAILED with SaveTimeout`() =
        runTest(mainDispatcherRule.testDispatcher) {
            taskRepository.hang = true

            editorCreate("Fregar")
            assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.save.editor)
            advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(EditorSaveStatus.FAILED, state.save.editor)
            assertEquals(TaskUiMessage.SaveTimeout, state.save.editorError)
            assertFalse(state.save.isSaving)
        }

    @Test
    fun `a validation error from the editor leaves FAILED`() {
        editorCreate("   ")

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        assertEquals(TaskUiMessage.TitleRequired, viewModel.uiState.value.save.editorError)
    }

    @Test
    fun `restoreTask does not touch the editor status`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.undo(Undoable.Deleted("home", task("t1", "Fregar", TaskType.LIMPIEZA)))
        advanceUntilIdle()

        assertEquals("Fregar", taskRepository.restored.single().title)
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.save.editor)
        assertTrue(viewModel.undoOffers.value.isEmpty())
    }

    @Test
    fun `undoing a delete keeps the id and the completed state`() = runTest(mainDispatcherRule.testDispatcher) {
        val done = task("t1", "Fregar", TaskType.LIMPIEZA).copy(status = TaskStatus.DONE)

        viewModel.undo(Undoable.Deleted("home", done))
        advanceUntilIdle()

        assertEquals("home", taskRepository.restoredSpaceId)
        assertEquals("t1", taskRepository.restored.single().id)
        assertEquals(TaskStatus.DONE, taskRepository.restored.single().status)
    }

    @Test
    fun `undoing a completed recurring task does not advance it`() = runTest(mainDispatcherRule.testDispatcher) {
        val done = task("t1", "Regar", null).copy(
            status = TaskStatus.DONE,
            recurrence = TaskRecurrence(RecurrenceUnit.WEEK, 1),
        )

        viewModel.undo(Undoable.Deleted("home", done))
        advanceUntilIdle()

        assertEquals(done, taskRepository.restored.single())
        assertNull(taskRepository.completedChange)
        assertNull(taskRepository.createdTitle)
    }

    @Test
    fun `a failed undo shows the error instead of staying silent`() = runTest(mainDispatcherRule.testDispatcher) {
        taskRepository.nextFailure = TaskRepositoryException(TaskFailure.PermissionDenied)

        viewModel.undo(Undoable.Deleted("home", task("t1", "Fregar", null)))
        advanceUntilIdle()

        assertEquals(TaskUiMessage.PermissionDenied, viewModel.uiState.value.save.error)
    }

    @Test
    fun `undo is offered only once the delete has finished`() = runTest(mainDispatcherRule.testDispatcher) {
        val fregar = task("t1", "Fregar", null)
        taskRepository.nextFailure = TaskRepositoryException(TaskFailure.Network)
        viewModel.deleteTask("home", fregar)
        runCurrent()
        assertTrue(viewModel.undoOffers.value.isEmpty())

        taskRepository.nextFailure = null
        viewModel.deleteTask("home", fregar)
        runCurrent()
        assertEquals(listOf(Undoable.Deleted("home", fregar)), viewModel.undoOffers.value.map { it.undoable })
        assertEquals(listOf("t1"), taskRepository.deleted)
    }

    @Test
    fun `deleting offers undo with the task and its space`() = runTest(mainDispatcherRule.testDispatcher) {
        val fregar = task("t1", "Fregar", null)

        viewModel.deleteTask("space-1", fregar)
        runCurrent()

        assertEquals(listOf(Undoable.Deleted("space-1", fregar)), viewModel.undoOffers.value.map { it.undoable })
    }

    @Test
    fun `an offer made while nobody listens waits and expires after UNDO_OFFER_TIMEOUT_MILLIS`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.deleteTask("space-1", task("t1", "Fregar", null))
            runCurrent()
            assertEquals(1, viewModel.undoOffers.value.size)

            advanceTimeBy(UNDO_OFFER_TIMEOUT_MILLIS - 1_000)
            runCurrent()
            assertEquals(1, viewModel.undoOffers.value.size)

            advanceTimeBy(1_001)
            runCurrent()
            assertTrue(viewModel.undoOffers.value.isEmpty())
        }

    @Test
    fun `undo of a delete goes to the space where it was deleted`() = runTest(mainDispatcherRule.testDispatcher) {
        val fregar = task("t1", "Fregar", null)
        viewModel.observe("space-1")
        viewModel.deleteTask("space-1", fregar)
        runCurrent()
        val offer = viewModel.undoOffers.value.single()

        viewModel.observe("space-2")
        viewModel.undo(offer.undoable)
        runCurrent()

        assertEquals("space-1", taskRepository.restoredSpaceId)
        assertEquals(listOf(fregar), taskRepository.restored)
    }

    @Test
    fun `two deletes leave two offers`() = runTest(mainDispatcherRule.testDispatcher) {
        val fregar = task("t1", "Fregar", null)
        val barrer = task("t2", "Barrer", null)

        viewModel.deleteTask("home", fregar)
        runCurrent()
        viewModel.deleteTask("home", barrer)
        runCurrent()

        assertEquals(
            listOf(Undoable.Deleted("home", fregar), Undoable.Deleted("home", barrer)),
            viewModel.undoOffers.value.map { it.undoable },
        )
    }

    @Test
    fun `undoing an add goes through undoCreateTask`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.undo(Undoable.Created("home", CreatedItem("new-1", "Fregar")))
        advanceUntilIdle()

        assertEquals(listOf("home" to "new-1"), taskRepository.createUndone)
        assertTrue(taskRepository.deleted.isEmpty())
    }

    @Test
    fun `an add another member changed is not undone and says so`() = runTest(mainDispatcherRule.testDispatcher) {
        taskRepository.nextFailure = TaskRepositoryException(TaskFailure.TaskChanged)

        viewModel.undo(Undoable.Created("home", CreatedItem("new-1", "Fregar")))
        advanceUntilIdle()

        assertEquals(TaskUiMessage.TaskChanged, viewModel.uiState.value.save.error)
        assertTrue(taskRepository.createUndone.isEmpty())
    }

    @Test
    fun `resetEditor returns to IDLE`() = runTest(mainDispatcherRule.testDispatcher) {
        editorCreate("Fregar")
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.save.editor)

        viewModel.feedback.resetEditor()

        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.save.editor)
    }

    @Test
    fun creatingOneItemOffersUndoWithItsIdAndName() = runTest(mainDispatcherRule.testDispatcher) {
        editorCreate("Leche")
        runCurrent()

        val offer = viewModel.undoOffers.value.single()
        assertEquals(Undoable.Created("home", CreatedItem("new-1", "Leche")), offer.undoable)
        assertNull(viewModel.uiState.value.save.notice)

        viewModel.undoControls.consumeOffer(offer.serial)
        assertTrue(viewModel.undoOffers.value.isEmpty())
    }

    @Test
    fun undoWhileAnotherActionIsSavingStillUndoes() = runTest(mainDispatcherRule.testDispatcher) {
        taskRepository.createGate = CompletableDeferred()
        editorCreate("Fregar")
        assertTrue(viewModel.uiState.value.save.isSaving)

        viewModel.undo(Undoable.Created("home", CreatedItem("new-1", "Fregar")))
        runCurrent() // sin avanzar el reloj: el alta del editor tiene tiempo máximo

        // El deshacer acaba antes, pero «Guardando…» sigue hasta que acaba también el alta.
        assertEquals(listOf("home" to "new-1"), taskRepository.createUndone)
        assertTrue(viewModel.uiState.value.save.isSaving)

        taskRepository.createGate?.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `a validation error while another write is in flight keeps saving and that write's error reaches the screen`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val gate = CompletableDeferred<Unit>()
            taskRepository.restoreGate = gate
            viewModel.undo(Undoable.Deleted("home", task("t1", "Fregar", null)))
            runCurrent()

            viewModel.showInvalidDate()

            val during = viewModel.uiState.value.save
            assertTrue(during.isSaving)
            assertEquals(TaskUiMessage.InvalidDate, during.editorError)
            assertNull(during.error)

            viewModel.feedback.resetEditor()
            taskRepository.nextFailure = TaskRepositoryException(TaskFailure.TaskNotFound)
            gate.complete(Unit)
            advanceUntilIdle()

            val after = viewModel.uiState.value.save
            assertEquals(TaskUiMessage.TaskNotFound, after.error)
            assertFalse(after.isSaving)
        }

    @Test
    fun `a forced undo that ends first keeps saving on and keeps the pending offer`() =
        runTest(mainDispatcherRule.testDispatcher) {
            taskRepository.createGate = CompletableDeferred()
            editorCreate("Fregar")
            runCurrent()

            val regar = task("t1", "Regar", null)
            viewModel.undo(Undoable.Completed("home", regar, regar))
            runCurrent() // sin avanzar el reloj: el alta del editor tiene tiempo máximo
            assertTrue(viewModel.uiState.value.save.isSaving)

            taskRepository.createGate?.complete(Unit)
            runCurrent()
            val created = Undoable.Created("home", CreatedItem("new-1", "Fregar"))
            assertEquals(listOf(created), viewModel.undoOffers.value.map { it.undoable })

            viewModel.undo(Undoable.Deleted("home", task("t2", "Pan", null)))
            runCurrent()
            assertEquals(listOf(created), viewModel.undoOffers.value.map { it.undoable })
        }

    @Test
    fun `marking a task done does not turn saving on`() = runTest(mainDispatcherRule.testDispatcher) {
        taskRepository.completeGate = CompletableDeferred()

        viewModel.setCompleted("home", task("t1", "Leche", null), true)
        runCurrent()

        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `an editor create after a failed mark keeps the mark error pending`() =
        runTest(mainDispatcherRule.testDispatcher) {
            taskRepository.nextFailure = TaskRepositoryException(TaskFailure.PermissionDenied)
            viewModel.setCompleted("home", task("t1", "Leche", null), true)
            advanceUntilIdle()
            assertEquals(TaskUiMessage.PermissionDenied, viewModel.uiState.value.save.error)
            taskRepository.nextFailure = null

            editorCreate("Fregar")
            advanceUntilIdle()

            assertEquals(TaskUiMessage.PermissionDenied, viewModel.uiState.value.save.error)
            assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.save.editor)
        }

    @Test
    fun editingDoesNotOfferUndo() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.updateTask("home", "t1", "Leche", null, "ana", null, TaskPriority.MEDIUM, null, null, null)
        advanceUntilIdle()

        assertTrue(viewModel.undoOffers.value.isEmpty())
    }

    private fun editorCreate(title: String) =
        viewModel.createTask("home", title, null, "ana", null, TaskPriority.MEDIUM, null, null, null)
}

private class FakeTaskRepository : TaskRepository {
    var observedSpaceId: String? = null
    var createdTitle: String? = null
    var createdType: TaskType? = null
    var createdAssignee: String? = null
    var createdAssigneeSet = false
    var completedChange: Triple<String, String, Boolean>? = null
    var completionDates: TaskDates? = TaskDates(null, null)
    var nextFailure: TaskRepositoryException? = null
    var loadFailure: Throwable? = null
    var createGate: CompletableDeferred<Unit>? = null
    var completeGate: CompletableDeferred<Unit>? = null
    var restoreGate: CompletableDeferred<Unit>? = null
    val completeStarted = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    val restored = mutableListOf<TaskItem>()
    var restoredSpaceId: String? = null
    val completionRestored = mutableListOf<TaskItem>()
    val completionRestoredTo = mutableListOf<TaskItem>()
    val createUndone = mutableListOf<Pair<String, String>>()
    var completionRestoredSpaceId: String? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false
    val tasksState = MutableStateFlow<List<TaskItem>>(emptyList())

    override fun tasks(spaceId: String): Flow<List<TaskItem>> {
        observedSpaceId = spaceId
        loadFailure?.let { return flow { throw it } }
        return tasksState
    }

    override suspend fun createTask(
        spaceId: String,
        title: String,
        description: String?,
        assigneeId: String?,
        dueAt: Instant?,
        priority: TaskPriority,
        type: TaskType?,
        recurrence: TaskRecurrence?,
        startAt: Instant?,
    ): String {
        throwNextFailure()
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                throw TaskRepositoryException(TaskFailure.Unknown)
            }
        }
        createGate?.await()
        createdTitle = title
        createdType = type
        createdAssignee = assigneeId
        createdAssigneeSet = true
        return "new-1"
    }

    override suspend fun updateTask(
        spaceId: String,
        taskId: String,
        title: String,
        description: String?,
        assigneeId: String?,
        dueAt: Instant?,
        priority: TaskPriority,
        type: TaskType?,
        recurrence: TaskRecurrence?,
        startAt: Instant?,
    ) {
        throwNextFailure()
    }

    override suspend fun setCompleted(spaceId: String, taskId: String, completed: Boolean): TaskDates? {
        completeStarted += taskId
        completeGate?.await()
        throwNextFailure()
        completedChange = Triple(spaceId, taskId, completed)
        return completionDates
    }

    override suspend fun undoCreateTask(spaceId: String, taskId: String) {
        throwNextFailure()
        createUndone += spaceId to taskId
    }

    override suspend fun deleteTask(spaceId: String, taskId: String) {
        throwNextFailure()
        deleted += taskId
    }

    override suspend fun restoreTask(spaceId: String, task: TaskItem) {
        restoreGate?.await()
        throwNextFailure()
        restoredSpaceId = spaceId
        restored += task
    }

    override suspend fun restoreCompletion(spaceId: String, previous: TaskItem, completed: TaskItem) {
        throwNextFailure()
        completionRestoredSpaceId = spaceId
        completionRestored += previous
        completionRestoredTo += completed
    }

    private fun throwNextFailure() {
        nextFailure?.let { throw it }
    }
}

private class FakeSpaceRepository : SpaceRepository {
    val membersState = MutableStateFlow<List<SpaceMember>>(emptyList())

    override fun members(spaceId: String): Flow<List<SpaceMember>> = membersState
    override fun spaces(): Flow<List<SpaceSummary>> = error("no usado en el test")
    override fun space(spaceId: String): Flow<SpaceSummary?> = error("no usado en el test")
    override fun invitations(spaceId: String): Flow<List<SpaceInvitation>> = error("no usado en el test")
    override suspend fun createSpace(name: String): String = error("no usado en el test")
    override suspend fun renameSpace(spaceId: String, name: String) {
        error("no usado en el test")
    }
    override suspend fun leaveSpace(spaceId: String) {
        error("no usado en el test")
    }
    override suspend fun removeMember(spaceId: String, userId: String) {
        error("no usado en el test")
    }
    override suspend fun transferOwnership(spaceId: String, newOwnerId: String) {
        error("no usado en el test")
    }
    override suspend fun deleteSpace(spaceId: String) {
        error("no usado en el test")
    }
    override suspend fun createInvitation(spaceId: String): SpaceInvitation = error("no usado en el test")
    override suspend fun revokeInvitation(invitationId: String) {
        error("no usado en el test")
    }
    override suspend fun acceptInvitation(code: String): String = error("no usado en el test")
}

private fun task(id: String, title: String, type: TaskType?) = TaskItem(
    id = id,
    title = title,
    description = null,
    assigneeId = null,
    assigneeName = null,
    dueAt = null,
    priority = TaskPriority.MEDIUM,
    status = TaskStatus.TODO,
    createdBy = "owner",
    createdByName = "David",
    createdAt = Instant.EPOCH,
    updatedBy = "owner",
    updatedAt = Instant.EPOCH,
    completedBy = null,
    completedByName = null,
    completedAt = null,
    type = type,
)
