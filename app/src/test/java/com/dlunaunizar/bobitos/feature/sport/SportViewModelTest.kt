package com.dlunaunizar.bobitos.feature.sport

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import com.dlunaunizar.bobitos.core.model.SpaceInvitation
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.core.model.SpaceRole
import com.dlunaunizar.bobitos.core.model.SpaceSummary
import com.dlunaunizar.bobitos.core.model.SportActivity
import com.dlunaunizar.bobitos.core.model.SportType
import com.dlunaunizar.bobitos.data.repository.RoutineRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.SportActivityRepository
import com.dlunaunizar.bobitos.data.repository.SportFailure
import com.dlunaunizar.bobitos.data.repository.SportRepositoryException
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSportActivityRepository()
    private val spaceRepository = FakeSpaceRepository()
    private val routineRepository = FakeSportRoutineRepository()
    private val viewModel = SportViewModel(repository, spaceRepository, routineRepository)

    @Test
    fun `observes activities and members for the active space`() = runTest(mainDispatcherRule.testDispatcher) {
        spaceRepository.membersState.value = listOf(SpaceMember("ana", "Ana", SpaceRole.MEMBER))
        repository.activitiesState.value = listOf(activity("a1", LocalDate.now(), SportType.PADEL, "Pádel"))

        viewModel.observe("home")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("home", repository.observedSpaceId)
        assertEquals(listOf("Pádel"), (state.activities as UiState.Content).value.map(SportActivity::name))
        assertEquals(listOf("Ana"), (state.members as UiState.Content).value.map(SpaceMember::displayName))
    }

    @Test
    fun `an invalid activity never reaches the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addActivity(LocalDate.now(), SportType.FUTBOL, "   ", emptyList())

        assertNull(repository.addedName)
        assertEquals(SportUiMessage.NameRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `adding an activity trims the name and reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addActivity(LocalDate.now(), SportType.GIMNASIO, "  Pecho  ", listOf("ana"))
        advanceUntilIdle()

        assertEquals("Pecho", repository.addedName)
        assertEquals(SportType.GIMNASIO, repository.addedType)
        assertEquals(SportUiMessage.ActivityAdded, viewModel.uiState.value.notice)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `adding a gym activity forwards its routine and session`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()
        val session = listOf(RoutineExercise("Sentadilla", type = ExerciseType.PESO_LIBRE))

        viewModel.addActivity(LocalDate.now(), SportType.GIMNASIO, "Pierna", emptyList(), "rutina-1", session)
        advanceUntilIdle()

        assertEquals("rutina-1", repository.addedRoutineId)
        assertEquals(session, repository.addedSession)
    }

    @Test
    fun `observes the routine catalog for the picker`() = runTest(mainDispatcherRule.testDispatcher) {
        routineRepository.mineState.value = listOf(routine("r1", "Empuje"))
        routineRepository.globalState.value = listOf(routine("r2", "Full body"))

        viewModel.observe("home")
        advanceUntilIdle()

        assertEquals(listOf("Empuje", "Full body"), viewModel.uiState.value.routines.map(Routine::title))
    }

    @Test
    fun `marking an activity done delegates to the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.setDone("a1", true)
        advanceUntilIdle()

        assertEquals("a1" to true, repository.doneChange)
        assertEquals(SportUiMessage.ActivityDone, viewModel.uiState.value.notice)
    }

    @Test
    fun `network failure is shown explicitly`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()
        repository.nextFailure = SportRepositoryException(SportFailure.Network)

        viewModel.deleteActivity("a1")
        advanceUntilIdle()

        assertEquals(SportUiMessage.NetworkError, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}

private class FakeSportActivityRepository : SportActivityRepository {
    val activitiesState = MutableStateFlow<List<SportActivity>>(emptyList())
    var observedSpaceId: String? = null
    var addedName: String? = null
    var addedType: SportType? = null
    var addedRoutineId: String? = null
    var addedSession: List<RoutineExercise> = emptyList()
    var doneChange: Pair<String, Boolean>? = null
    var nextFailure: SportRepositoryException? = null

    override fun activities(
        spaceId: String,
        weekStart: LocalDate,
        weekEndExclusive: LocalDate,
    ): Flow<List<SportActivity>> {
        observedSpaceId = spaceId
        return activitiesState
    }

    override suspend fun addActivity(
        spaceId: String,
        date: LocalDate,
        type: SportType,
        name: String,
        participantIds: List<String>,
        routineId: String?,
        session: List<RoutineExercise>,
    ) {
        throwNextFailure()
        addedName = name
        addedType = type
        addedRoutineId = routineId
        addedSession = session
    }

    override suspend fun updateActivity(
        spaceId: String,
        activityId: String,
        date: LocalDate,
        type: SportType,
        name: String,
        participantIds: List<String>,
        routineId: String?,
        session: List<RoutineExercise>,
    ) {
        throwNextFailure()
    }

    override suspend fun setDone(spaceId: String, activityId: String, done: Boolean) {
        throwNextFailure()
        doneChange = activityId to done
    }

    override suspend fun deleteActivity(spaceId: String, activityId: String) {
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
    override suspend fun renameSpace(spaceId: String, name: String) = error("no usado en el test")
    override suspend fun leaveSpace(spaceId: String) = error("no usado en el test")
    override suspend fun removeMember(spaceId: String, userId: String) = error("no usado en el test")
    override suspend fun transferOwnership(spaceId: String, newOwnerId: String) = error("no usado en el test")
    override suspend fun deleteSpace(spaceId: String) = error("no usado en el test")
    override suspend fun createInvitation(spaceId: String): SpaceInvitation = error("no usado en el test")
    override suspend fun revokeInvitation(invitationId: String) = error("no usado en el test")
    override suspend fun acceptInvitation(code: String): String = error("no usado en el test")
}

private class FakeSportRoutineRepository : RoutineRepository {
    val globalState = MutableStateFlow<List<Routine>>(emptyList())
    val mineState = MutableStateFlow<List<Routine>>(emptyList())

    override fun globalRoutines(): Flow<List<Routine>> = globalState
    override fun myRoutines(): Flow<List<Routine>> = mineState
    override fun isCurrentUserRoutineAdmin(): Boolean = false
    override suspend fun createRoutine(
        visibility: RoutineVisibility,
        title: String,
        description: String?,
        exercises: List<RoutineExercise>,
    ) = Unit

    override suspend fun updateRoutine(
        routineId: String,
        title: String,
        description: String?,
        exercises: List<RoutineExercise>,
    ) = Unit

    override suspend fun deleteRoutine(routineId: String) = Unit
}

private fun routine(id: String, title: String) = Routine(
    id = id,
    ownerUid = "u",
    visibility = RoutineVisibility.PRIVATE,
    title = title,
    description = null,
    exercises = null,
    createdBy = "u",
    createdByName = "U",
    createdAt = Instant.EPOCH,
    updatedBy = "u",
    updatedAt = Instant.EPOCH,
)

private fun activity(id: String, date: LocalDate, type: SportType, name: String) = SportActivity(
    id = id,
    date = date,
    type = type,
    name = name,
    participantIds = emptyList(),
    participantNames = emptyList(),
    createdBy = "owner",
    createdByName = "David",
    createdAt = Instant.EPOCH,
    updatedBy = "owner",
    updatedAt = Instant.EPOCH,
)
