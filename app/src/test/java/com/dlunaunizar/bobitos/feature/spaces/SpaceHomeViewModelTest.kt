package com.dlunaunizar.bobitos.feature.spaces

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.Meal
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.core.model.TaskItem
import com.dlunaunizar.bobitos.core.model.TaskPriority
import com.dlunaunizar.bobitos.core.model.TaskStatus
import com.dlunaunizar.bobitos.data.repository.CalendarRepository
import com.dlunaunizar.bobitos.data.repository.EventInput
import com.dlunaunizar.bobitos.data.repository.MealRepository
import com.dlunaunizar.bobitos.data.repository.SpaceModuleCounts
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.SpaceSummaryRepository
import com.dlunaunizar.bobitos.data.repository.TaskRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SpaceHomeViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val me = "me"
    private val members = mutableMapOf<String, MutableSharedFlow<List<SpaceMember>>>()
    private val tasks = mutableMapOf<String, MutableSharedFlow<List<TaskItem>>>()
    private val meals = mutableMapOf<String, MutableSharedFlow<List<Meal>>>()
    private val events = mutableMapOf<String, MutableSharedFlow<List<CalendarEvent>>>()

    private fun <T> MutableMap<String, MutableSharedFlow<T>>.of(space: String) =
        getOrPut(space) { MutableSharedFlow(replay = 1) }

    private var countsBlock: suspend () -> SpaceModuleCounts = { SpaceModuleCounts() }

    private val viewModel = SpaceHomeViewModel(
        summaryRepository = object : SpaceSummaryRepository {
            override suspend fun counts(spaceId: String): SpaceModuleCounts = countsBlock()
        },
        taskRepository = object : TaskRepository by NoTasks {
            override fun tasks(spaceId: String): Flow<List<TaskItem>> =
                if (spaceId == "bad") flow { throw IllegalStateException("sin conexión") } else tasks.of(spaceId)
        },
        mealRepository = object : MealRepository by NoMeals {
            override fun meals(spaceId: String, weekStart: LocalDate, weekEndExclusive: LocalDate) = meals.of(spaceId)
        },
        calendarRepository = object : CalendarRepository by NoEvents {
            override fun events(spaceId: String, rangeStart: Instant, rangeEndExclusive: Instant) = events.of(spaceId)
        },
        spaceRepository = object : SpaceRepository by NoSpaces {
            override fun members(spaceId: String): Flow<List<SpaceMember>> = members.of(spaceId)
        },
    )

    private fun emitAll(space: String, taskList: List<TaskItem> = emptyList()) {
        members.of(space).tryEmit(listOf(SpaceMember(me, "Yo", SpaceRole.OWNER)))
        tasks.of(space).tryEmit(taskList)
        meals.of(space).tryEmit(emptyList())
        events.of(space).tryEmit(emptyList())
    }

    @Test
    fun switchingSpaceClearsTheOldDigest() {
        emitAll("a")
        viewModel.load("a", me)
        assertNotNull(viewModel.digest.value)

        viewModel.load("b", me)
        assertNull(viewModel.digest.value)

        emitAll("b")
        assertNotNull(viewModel.digest.value)
    }

    @Test
    fun reloadingTheSameSpaceKeepsWhatIsShown() {
        emitAll("a")
        viewModel.load("a", me)
        assertNotNull(viewModel.digest.value)
        assertNotNull(viewModel.counts.value)

        // La recarga no recibe nada al instante: lo que había se queda.
        tasks.of("a").resetReplayCache()
        members.of("a").resetReplayCache()
        meals.of("a").resetReplayCache()
        events.of("a").resetReplayCache()
        countsBlock = { awaitCancellation() }
        viewModel.load("a", me)

        assertNotNull(viewModel.digest.value)
        assertNotNull(viewModel.counts.value)
    }

    @Test
    fun aFailingCountsReloadOfTheSameSpaceKeepsTheLastCounts() {
        emitAll("a")
        viewModel.load("a", me)
        assertNotNull(viewModel.counts.value)

        countsBlock = { throw IllegalStateException("sin conexión") }
        viewModel.load("a", me)

        assertNotNull(viewModel.counts.value)
    }

    @Test
    fun aLaterServerSnapshotUpdatesTheDigest() {
        emitAll("a")
        viewModel.load("a", me)
        assertEquals(0, viewModel.digest.value?.todayItemCount())

        tasks.of("a").tryEmit(listOf(myTaskDueToday()))
        assertEquals(1, viewModel.digest.value?.todayItemCount())
    }

    @Test
    fun aFailingDigestSourceStillEndsTheLoading() {
        emitAll("bad")
        viewModel.load("bad", me)

        val digest = viewModel.digest.value
        assertNotNull(digest)
        assertEquals(0, digest?.todayItemCount())
    }

    private fun myTaskDueToday(): TaskItem {
        val zone = ZoneId.systemDefault()
        return TaskItem(
            id = "t1",
            title = "t1",
            description = null,
            assigneeId = me,
            assigneeName = "Yo",
            dueAt = LocalDate.now(zone).atStartOfDay(zone).toInstant(),
            priority = TaskPriority.MEDIUM,
            status = TaskStatus.TODO,
            createdBy = me,
            createdByName = "Yo",
            createdAt = Instant.EPOCH,
            updatedBy = me,
            updatedAt = Instant.EPOCH,
            completedBy = null,
            completedByName = null,
            completedAt = null,
        )
    }
}

// Los fakes solo implementan la lectura que usa el ViewModel; el resto no se llama.
private object NoTasks : TaskRepository {
    override fun tasks(spaceId: String): Flow<List<TaskItem>> = error("no usado")
    override suspend fun createTask(
        spaceId: String,
        title: String,
        description: String?,
        assigneeId: String?,
        dueAt: Instant?,
        priority: TaskPriority,
        type: com.dlunaunizar.bobitos.core.model.TaskType?,
        recurrence: com.dlunaunizar.bobitos.core.model.TaskRecurrence?,
        startAt: Instant?,
    ): String = error("no usado")

    override suspend fun updateTask(
        spaceId: String,
        taskId: String,
        title: String,
        description: String?,
        assigneeId: String?,
        dueAt: Instant?,
        priority: TaskPriority,
        type: com.dlunaunizar.bobitos.core.model.TaskType?,
        recurrence: com.dlunaunizar.bobitos.core.model.TaskRecurrence?,
        startAt: Instant?,
    ): Unit = error("no usado")

    override suspend fun setCompleted(spaceId: String, taskId: String, completed: Boolean): Unit = error("no usado")
    override suspend fun deleteTask(spaceId: String, taskId: String): Unit = error("no usado")
}

private object NoMeals : MealRepository {
    override fun meals(spaceId: String, weekStart: LocalDate, weekEndExclusive: LocalDate): Flow<List<Meal>> {
        error("no usado")
    }

    override suspend fun addMeal(
        spaceId: String,
        date: LocalDate,
        slot: com.dlunaunizar.bobitos.core.model.MealSlot,
        name: String,
        participantIds: List<String>,
        recipeId: String?,
        cookId: String?,
    ): String = error("no usado")

    override suspend fun updateMeal(
        spaceId: String,
        mealId: String,
        date: LocalDate,
        slot: com.dlunaunizar.bobitos.core.model.MealSlot,
        name: String,
        participantIds: List<String>,
        recipeId: String?,
        cookId: String?,
    ): Unit = error("no usado")

    override suspend fun setCooked(spaceId: String, mealId: String, cooked: Boolean): Unit = error("no usado")
    override suspend fun deleteMeal(spaceId: String, mealId: String): Unit = error("no usado")
}

private object NoEvents : CalendarRepository {
    override fun events(spaceId: String, rangeStart: Instant, rangeEndExclusive: Instant): Flow<List<CalendarEvent>> {
        error("no usado")
    }

    override suspend fun createEvent(spaceId: String, input: EventInput, eventId: String?): String = error("no usado")
    override suspend fun updateEvent(spaceId: String, eventId: String, input: EventInput): Unit = error("no usado")
    override suspend fun deleteEvent(spaceId: String, eventId: String): Unit = error("no usado")
}

private object NoSpaces : SpaceRepository {
    override fun spaces(): Flow<List<SpaceSummary>> = error("no usado")
    override fun space(spaceId: String): Flow<SpaceSummary?> = error("no usado")
    override fun members(spaceId: String): Flow<List<SpaceMember>> = error("no usado")
    override fun invitations(spaceId: String): Flow<List<SpaceInvitation>> = error("no usado")
    override suspend fun createSpace(name: String): String = error("no usado")
    override suspend fun renameSpace(spaceId: String, name: String): Unit = error("no usado")
    override suspend fun leaveSpace(spaceId: String): Unit = error("no usado")
    override suspend fun removeMember(spaceId: String, userId: String): Unit = error("no usado")
    override suspend fun transferOwnership(spaceId: String, newOwnerId: String): Unit = error("no usado")
    override suspend fun deleteSpace(spaceId: String): Unit = error("no usado")
    override suspend fun createInvitation(spaceId: String): SpaceInvitation = error("no usado")
    override suspend fun revokeInvitation(invitationId: String): Unit = error("no usado")
    override suspend fun acceptInvitation(code: String): String = error("no usado")
}
