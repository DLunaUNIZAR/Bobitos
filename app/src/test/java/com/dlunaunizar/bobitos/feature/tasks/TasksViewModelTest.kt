package com.dlunaunizar.bobitos.feature.tasks

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.UiState
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals(TaskUiMessage.TaskCreated, viewModel.uiState.value.notice)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `an invalid task never reaches the repository`() {
        viewModel.createTask("home", "   ", null, "ana", null, TaskPriority.LOW, null, null, null)

        assertNull(taskRepository.createdTitle)
        assertEquals(TaskUiMessage.TitleRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `marking a task delegates to the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.setCompleted("home", "t1", true)
        advanceUntilIdle()

        assertEquals(Triple("home", "t1", true), taskRepository.completedChange)
        assertEquals(TaskUiMessage.TaskCompleted, viewModel.uiState.value.notice)
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
    fun `filtering by type keeps only that category`() {
        val tasks = listOf(
            task("t1", "Fregar", TaskType.LIMPIEZA),
            task("t2", "Médico", TaskType.MEDICO),
            task("t3", "Sin tipo", null),
        )

        val filtered = TaskFilters(status = null, type = TaskType.LIMPIEZA).apply(tasks)

        assertEquals(listOf("Fregar"), filtered.map(TaskItem::title))
    }
}

private class FakeTaskRepository : TaskRepository {
    var observedSpaceId: String? = null
    var createdTitle: String? = null
    var createdType: TaskType? = null
    var completedChange: Triple<String, String, Boolean>? = null
    var nextFailure: TaskRepositoryException? = null
    val tasksState = MutableStateFlow<List<TaskItem>>(emptyList())

    override fun tasks(spaceId: String): Flow<List<TaskItem>> {
        observedSpaceId = spaceId
        return tasksState
    }

    override suspend fun createTask(
        spaceId: String,
        title: String,
        description: String?,
        assigneeId: String,
        dueAt: Instant?,
        priority: TaskPriority,
        type: TaskType?,
        recurrence: TaskRecurrence?,
        startAt: Instant?,
    ) {
        throwNextFailure()
        createdTitle = title
        createdType = type
    }

    override suspend fun updateTask(
        spaceId: String,
        taskId: String,
        title: String,
        description: String?,
        assigneeId: String,
        dueAt: Instant?,
        priority: TaskPriority,
        type: TaskType?,
        recurrence: TaskRecurrence?,
        startAt: Instant?,
    ) {
        throwNextFailure()
    }

    override suspend fun setCompleted(spaceId: String, taskId: String, completed: Boolean) {
        throwNextFailure()
        completedChange = Triple(spaceId, taskId, completed)
    }

    override suspend fun deleteTask(spaceId: String, taskId: String) {
        throwNextFailure()
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
