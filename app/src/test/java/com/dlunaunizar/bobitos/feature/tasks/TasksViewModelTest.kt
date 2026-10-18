package com.dlunaunizar.bobitos.feature.tasks

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.FailureKind
import com.dlunaunizar.bobitos.core.common.UiState
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
import com.dlunaunizar.bobitos.data.repository.TaskFailure
import com.dlunaunizar.bobitos.data.repository.TaskRepository
import com.dlunaunizar.bobitos.data.repository.TaskRepositoryException
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
    private val viewModel = TasksViewModel(taskRepository, spaceRepository)

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
        advanceUntilIdle()

        assertEquals("Fregar", taskRepository.createdTitle)
        assertEquals(TaskType.LIMPIEZA, taskRepository.createdType)
        assertNull(viewModel.uiState.value.notice)
        assertEquals(CreatedItem("new-1", "Fregar"), viewModel.uiState.value.created)
        assertFalse(viewModel.uiState.value.isSaving)
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
            assertNull(viewModel.uiState.value.notice)
        }

    @Test
    fun `an invalid task never reaches the repository`() {
        viewModel.createTask("home", "   ", null, "ana", null, TaskPriority.LOW, null, null, null)

        assertNull(taskRepository.createdTitle)
        assertEquals(TaskUiMessage.TitleRequired, viewModel.uiState.value.error)
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
        assertFalse(viewModel.uiState.value.isSaving)
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
    fun completarOfreceDeshacerConLaFotoDeAntes() = runTest(mainDispatcherRule.testDispatcher) {
        val start = Instant.parse("2026-10-01T08:00:00Z")
        val due = Instant.parse("2026-10-02T08:00:00Z")
        val antes = task("t1", "Regar", null).copy(startAt = start, dueAt = due)
        var ofrecida: TaskItem? = null

        viewModel.setCompleted("home", antes, true) { ofrecida = it }
        advanceUntilIdle()

        assertEquals(antes, ofrecida)
        assertEquals(start, ofrecida?.startAt)
        assertEquals(due, ofrecida?.dueAt)
        assertNull(viewModel.uiState.value.notice)
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

        viewModel.undoCompletion("home", antes)
        advanceUntilIdle()

        assertEquals("home", taskRepository.completionRestoredSpaceId)
        assertEquals(antes, taskRepository.completionRestored.single())
        assertNull(taskRepository.completedChange)
    }

    @Test
    fun deshacerSiLaTareaYaNoExisteAvisa() = runTest(mainDispatcherRule.testDispatcher) {
        taskRepository.nextFailure = TaskRepositoryException(TaskFailure.TaskNotFound)

        viewModel.undoCompletion("home", task("t1", "Regar", null))
        advanceUntilIdle()

        assertEquals(TaskUiMessage.TaskNotFound, viewModel.uiState.value.error)
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

        viewModel.deleteTask("home", "t1")
        advanceUntilIdle()

        assertEquals(TaskUiMessage.NetworkError, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
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
            assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
            assertTrue(viewModel.uiState.value.isSaving)

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
            assertFalse(viewModel.uiState.value.isSaving)
        }

    @Test
    fun `editor save failure leaves FAILED with the error and isSaving false`() =
        runTest(mainDispatcherRule.testDispatcher) {
            taskRepository.nextFailure = TaskRepositoryException(TaskFailure.Network)

            editorCreate("Fregar")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(EditorSaveStatus.FAILED, state.editorSave)
            assertEquals(TaskUiMessage.NetworkError, state.error)
            assertFalse(state.isSaving)
        }

    @Test
    fun `editor save without answer times out into FAILED with SaveTimeout`() =
        runTest(mainDispatcherRule.testDispatcher) {
            taskRepository.hang = true

            editorCreate("Fregar")
            assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
            advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(EditorSaveStatus.FAILED, state.editorSave)
            assertEquals(TaskUiMessage.SaveTimeout, state.error)
            assertFalse(state.isSaving)
        }

    @Test
    fun `a validation error from the editor leaves FAILED`() {
        editorCreate("   ")

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(TaskUiMessage.TitleRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `restoreTask does not touch the editor status`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.restoreTask("home", task("t1", "Fregar", TaskType.LIMPIEZA))
        advanceUntilIdle()

        assertEquals("Fregar", taskRepository.restored.single().title)
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
        assertNull(viewModel.uiState.value.created)
    }

    @Test
    fun `undoing a delete keeps the id and the completed state`() = runTest(mainDispatcherRule.testDispatcher) {
        val done = task("t1", "Fregar", TaskType.LIMPIEZA).copy(status = TaskStatus.DONE)

        viewModel.restoreTask("home", done)
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

        viewModel.restoreTask("home", done)
        advanceUntilIdle()

        assertEquals(done, taskRepository.restored.single())
        assertNull(taskRepository.completedChange)
        assertNull(taskRepository.createdTitle)
    }

    @Test
    fun `a failed undo shows the error instead of staying silent`() = runTest(mainDispatcherRule.testDispatcher) {
        taskRepository.nextFailure = TaskRepositoryException(TaskFailure.PermissionDenied)

        viewModel.restoreTask("home", task("t1", "Fregar", null))
        advanceUntilIdle()

        assertEquals(TaskUiMessage.PermissionDenied, viewModel.uiState.value.error)
    }

    @Test
    fun `undo is offered only once the delete has finished`() = runTest(mainDispatcherRule.testDispatcher) {
        var offered = 0
        taskRepository.nextFailure = TaskRepositoryException(TaskFailure.Network)
        viewModel.deleteTask("home", "t1") { offered++ }
        advanceUntilIdle()
        assertEquals(0, offered)

        taskRepository.nextFailure = null
        viewModel.deleteTask("home", "t1") { offered++ }
        advanceUntilIdle()
        assertEquals(1, offered)
        assertEquals(listOf("t1"), taskRepository.deleted)
    }

    @Test
    fun `consumeEditorSave returns to IDLE`() = runTest(mainDispatcherRule.testDispatcher) {
        editorCreate("Fregar")
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)

        viewModel.consumeEditorSave()

        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
    }

    @Test
    fun creatingOneItemOffersUndoWithItsIdAndName() = runTest(mainDispatcherRule.testDispatcher) {
        editorCreate("Leche")
        advanceUntilIdle()

        assertEquals(CreatedItem("new-1", "Leche"), viewModel.uiState.value.created)
        assertNull(viewModel.uiState.value.notice)

        viewModel.consumeCreated()
        assertNull(viewModel.uiState.value.created)
    }

    @Test
    fun undoCreateDeletesExactlyThatItem() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        viewModel.undoCreate("new-1")
        advanceUntilIdle()

        assertEquals(listOf("new-1"), taskRepository.deleted)
    }

    @Test
    fun undoWhileAnotherActionIsSavingStillDeletes() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        taskRepository.createGate = CompletableDeferred()
        editorCreate("Fregar")
        assertTrue(viewModel.uiState.value.isSaving)

        viewModel.undoCreate("new-1")
        advanceUntilIdle()

        assertEquals(listOf("new-1"), taskRepository.deleted)
    }

    @Test
    fun editingDoesNotOfferUndo() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.updateTask("home", "t1", "Leche", null, "ana", null, TaskPriority.MEDIUM, null, null, null)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.created)
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
    var nextFailure: TaskRepositoryException? = null
    var loadFailure: Throwable? = null
    var createGate: CompletableDeferred<Unit>? = null
    var completeGate: CompletableDeferred<Unit>? = null
    val completeStarted = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    val restored = mutableListOf<TaskItem>()
    var restoredSpaceId: String? = null
    val completionRestored = mutableListOf<TaskItem>()
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

    override suspend fun setCompleted(spaceId: String, taskId: String, completed: Boolean) {
        completeStarted += taskId
        completeGate?.await()
        throwNextFailure()
        completedChange = Triple(spaceId, taskId, completed)
    }

    override suspend fun deleteTask(spaceId: String, taskId: String) {
        throwNextFailure()
        deleted += taskId
    }

    override suspend fun restoreTask(spaceId: String, task: TaskItem) {
        throwNextFailure()
        restoredSpaceId = spaceId
        restored += task
    }

    override suspend fun restoreCompletion(spaceId: String, previous: TaskItem) {
        throwNextFailure()
        completionRestoredSpaceId = spaceId
        completionRestored += previous
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
