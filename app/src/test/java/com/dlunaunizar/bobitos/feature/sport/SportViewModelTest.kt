package com.dlunaunizar.bobitos.feature.sport

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.FailureKind
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.Undoable
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.EventColor
import com.dlunaunizar.bobitos.core.model.ExerciseInput
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
import com.dlunaunizar.bobitos.data.repository.ExerciseRepository
import com.dlunaunizar.bobitos.data.repository.RoutineRepository
import com.dlunaunizar.bobitos.data.repository.SpaceRepository
import com.dlunaunizar.bobitos.data.repository.SportActivityRepository
import com.dlunaunizar.bobitos.data.repository.SportFailure
import com.dlunaunizar.bobitos.data.repository.SportRepositoryException
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSportActivityRepository()
    private val spaceRepository = FakeSpaceRepository()
    private val routineRepository = FakeSportRoutineRepository()
    private val exerciseRepository = FakeSportExerciseRepository()
    private val viewModel by lazy { SportViewModel(repository, spaceRepository, routineRepository, exerciseRepository) }

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
        assertEquals(SportUiMessage.NameRequired, viewModel.uiState.value.save.editorError)
    }

    @Test
    fun `adding an activity trims the name and reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addActivity(LocalDate.now(), SportType.GIMNASIO, "  Pecho  ", listOf("ana"))
        advanceUntilIdle()

        assertEquals("Pecho", repository.addedName)
        assertEquals(SportType.GIMNASIO, repository.addedType)
        assertNull(viewModel.uiState.value.save.notice)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun creatingOneItemOffersUndoWithItsIdNameAndSpace() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addActivity(LocalDate.now(), SportType.PADEL, "  Pádel ", emptyList())
        runCurrent()

        assertEquals(
            listOf(Undoable.Created("home", CreatedItem("new-1", "Pádel"))),
            viewModel.undoOffers.value.map { it.undoable },
        )
        assertNull(viewModel.uiState.value.save.notice)
    }

    @Test
    fun `undoing an add goes through undoAddActivity`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.undo(Undoable.Created("home", CreatedItem("new-1", "Pádel")))
        runCurrent()

        assertEquals(listOf("home" to "new-1"), repository.undoneAdds)
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun `an add another member changed is not undone and says so`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()
        repository.nextFailure = SportRepositoryException(SportFailure.ActivityChanged)

        viewModel.undo(Undoable.Created("home", CreatedItem("new-1", "Pádel")))
        runCurrent()

        assertEquals(SportUiMessage.ActivityChanged, viewModel.uiState.value.save.error)
        assertTrue(repository.undoneAdds.isEmpty())
    }

    @Test
    fun editingDoesNotOfferUndo() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        viewModel.updateActivity("a1", LocalDate.now(), SportType.PADEL, "Pádel", emptyList())
        runCurrent()

        assertTrue(viewModel.undoOffers.value.isEmpty())
    }

    @Test
    fun aRestoredActivityNeverOffersUndo() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.undo(
            Undoable.Deleted("home", DeletedActivity(activity("a1", LocalDate.now(), SportType.PADEL, "P"), null)),
        )
        runCurrent()

        assertTrue(viewModel.undoOffers.value.isEmpty())
    }

    @Test
    fun `deleting offers undo with the activity, its event and its space`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.observe("home")
            advanceUntilIdle()
            val done = activity("a1", LocalDate.now(), SportType.GIMNASIO, "Pierna").copy(done = true, eventId = "e1")
            val event = linkedEvent("e1", "Pierna (editado)")
            repository.deletedEvent = event

            viewModel.deleteActivity(done)
            runCurrent()

            assertEquals(
                listOf(Undoable.Deleted("home", DeletedActivity(done, event))),
                viewModel.undoOffers.value.map { it.undoable },
            )
            assertNull(viewModel.uiState.value.save.notice)
        }

    @Test
    fun `undoing a delete keeps ids and done`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()
        val done = activity("a1", LocalDate.now(), SportType.GIMNASIO, "Pierna").copy(done = true, eventId = "e1")
        // La foto del evento que devolvió el borrado: editado desde el Calendario (título distinto).
        repository.deletedEvent = linkedEvent("e1", "Pierna (editado)")

        viewModel.deleteActivity(done)
        runCurrent()
        viewModel.undo(viewModel.undoOffers.value.single().undoable)
        runCurrent()

        val restored = repository.restored.single()
        assertEquals("home", restored.spaceId)
        assertEquals("a1", restored.activity.id)
        assertTrue(restored.activity.done)
        assertEquals("e1", restored.event?.id)
        assertEquals("Pierna (editado)", restored.event?.title)
        assertNull(viewModel.uiState.value.save.notice)
    }

    @Test
    fun `undo of a delete goes to the space where it was deleted`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("space-a")
        advanceUntilIdle()
        viewModel.deleteActivity(activity("a1", LocalDate.now(), SportType.PADEL, "Pádel"))
        runCurrent()
        val offer = viewModel.undoOffers.value.single().undoable
        viewModel.observe("space-b")
        advanceUntilIdle()

        viewModel.undo(offer)
        runCurrent()

        assertEquals("space-a", repository.restored.single().spaceId)
    }

    @Test
    fun `undo is offered only once the delete has finished`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        val victim = activity("a1", LocalDate.now(), SportType.PADEL, "Pádel")
        repository.nextFailure = SportRepositoryException(SportFailure.Network)
        viewModel.deleteActivity(victim)
        runCurrent()
        assertTrue(viewModel.undoOffers.value.isEmpty())
        assertEquals(SportUiMessage.NetworkError, viewModel.uiState.value.save.error)

        repository.nextFailure = null
        viewModel.deleteActivity(victim)
        runCurrent()
        assertEquals(1, viewModel.undoOffers.value.size)
    }

    @Test
    fun `a failed undo surfaces the error`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        repository.nextFailure = SportRepositoryException(SportFailure.SpaceNotFound)

        viewModel.undo(
            Undoable.Deleted("home", DeletedActivity(activity("a1", LocalDate.now(), SportType.PADEL, "Pádel"), null)),
        )
        advanceUntilIdle()

        assertEquals(SportUiMessage.SpaceNotFound, viewModel.uiState.value.save.error)
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
    fun `the picker lists my private routines first and then every common one`() =
        runTest(mainDispatcherRule.testDispatcher) {
            routineRepository.mineState.value = listOf(
                routine("a-comun", "A común", RoutineVisibility.GLOBAL),
                routine("z-mia", "Z mía"),
            )
            routineRepository.globalState.value = listOf(
                routine("a-comun", "A común", RoutineVisibility.GLOBAL),
                routine("b-comun", "B común", RoutineVisibility.GLOBAL),
            )

            viewModel.observe("home")
            advanceUntilIdle()

            assertEquals(listOf("z-mia", "a-comun", "b-comun"), viewModel.uiState.value.routines.map(Routine::id))
        }

    @Test
    fun `does not subscribe to the exercise catalog until the session editor needs it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            exerciseRepository.catalogState.value = listOf(catalogExercise("press-banca", "Press banca"))

            viewModel.observe("home")
            advanceUntilIdle()
            assertEquals(0, exerciseRepository.catalogCalls)

            viewModel.observeExerciseCatalog()
            viewModel.observeExerciseCatalog()
            advanceUntilIdle()

            assertEquals(1, exerciseRepository.catalogCalls)
            assertEquals(listOf("Press banca"), viewModel.uiState.value.exercises.map(CatalogExercise::name))
        }

    @Test
    fun `catalog resumes after switching space with the session editor open`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.observe("home")
            viewModel.observeExerciseCatalog()
            advanceUntilIdle()

            // Cambio de espacio: la pantalla para y vuelve a observar, sin que el editor pida el catálogo otra vez.
            viewModel.stopObserving()
            viewModel.observe("work")
            advanceUntilIdle()
            exerciseRepository.catalogState.value = listOf(catalogExercise("press-banca", "Press banca"))
            advanceUntilIdle()

            assertEquals(2, exerciseRepository.catalogCalls)
            assertEquals(listOf("Press banca"), viewModel.uiState.value.exercises.map(CatalogExercise::name))
        }

    @Test
    fun `observing the exercise catalog again after stopObserving resubscribes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.observe("home")
            viewModel.observeExerciseCatalog()
            advanceUntilIdle()
            viewModel.stopObserving()

            viewModel.observe("home")
            viewModel.observeExerciseCatalog()
            advanceUntilIdle()

            assertEquals(2, exerciseRepository.catalogCalls)
        }

    @Test
    fun `marking an activity done delegates to the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.setDone("a1", true)
        advanceUntilIdle()

        assertEquals("a1" to true, repository.doneChange)
        assertEquals(SportUiMessage.ActivityDone, viewModel.uiState.value.save.notice)
    }

    @Test
    fun aLoadFailureNeverExposesTheExceptionText() = runTest(mainDispatcherRule.testDispatcher) {
        repository.loadFailure = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")

        viewModel.observe("home")
        advanceUntilIdle()

        assertEquals(UiState.Error(FailureKind.UNKNOWN), viewModel.uiState.value.activities)
    }

    @Test
    fun `network failure is shown explicitly`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()
        repository.nextFailure = SportRepositoryException(SportFailure.Network)

        viewModel.deleteActivity(activity("a1", LocalDate.now(), SportType.PADEL, "Pádel"))
        advanceUntilIdle()

        assertEquals(SportUiMessage.NetworkError, viewModel.uiState.value.save.error)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `editor save is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.addActivity(LocalDate.now(), SportType.PADEL, "Pádel", emptyList())
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
    fun `editor save failure or timeout leaves FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()
        repository.nextFailure = SportRepositoryException(SportFailure.Network)
        viewModel.addActivity(LocalDate.now(), SportType.PADEL, "Pádel", emptyList())
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        assertEquals(SportUiMessage.NetworkError, viewModel.uiState.value.save.editorError)
        assertFalse(viewModel.uiState.value.save.isSaving)

        repository.nextFailure = null
        viewModel.feedback.resetEditor()
        repository.hang = true
        viewModel.addActivity(LocalDate.now(), SportType.PADEL, "Pádel", emptyList())
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.save.editor)
        advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        assertEquals(SportUiMessage.SaveTimeout, viewModel.uiState.value.save.editorError)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `restoreActivity does not touch the editor status`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        advanceUntilIdle()

        viewModel.undo(
            Undoable.Deleted("home", DeletedActivity(activity("a1", LocalDate.now(), SportType.PADEL, "Pádel"), null)),
        )
        advanceUntilIdle()

        assertEquals("Pádel", repository.restored.single().activity.name)
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.save.editor)
        assertNull(viewModel.uiState.value.save.notice)
    }

    @Test
    fun `a forced restore that ends first keeps saving on until the other write ends`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.addGate = CompletableDeferred()
            viewModel.observe("home")
            advanceUntilIdle()
            viewModel.addActivity(LocalDate.now(), SportType.PADEL, "Pádel", emptyList())
            runCurrent() // sin avanzar el reloj: el alta del editor tiene tiempo máximo

            viewModel.undo(
                Undoable.Deleted(
                    "home",
                    DeletedActivity(activity("a1", LocalDate.now(), SportType.PADEL, "Pádel"), null),
                ),
            )
            runCurrent()

            assertEquals("a1", repository.restored.single().activity.id)
            assertTrue(viewModel.uiState.value.save.isSaving)

            repository.addGate?.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.save.isSaving)
        }
}

private class FakeSportActivityRepository : SportActivityRepository {
    var addGate: CompletableDeferred<Unit>? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    val activitiesState = MutableStateFlow<List<SportActivity>>(emptyList())
    var observedSpaceId: String? = null
    var addedName: String? = null
    var addedType: SportType? = null
    var addedRoutineId: String? = null
    var addedSession: List<RoutineExercise> = emptyList()
    var doneChange: Pair<String, Boolean>? = null
    var nextFailure: SportRepositoryException? = null
    var loadFailure: Throwable? = null

    override fun activities(
        spaceId: String,
        weekStart: LocalDate,
        weekEndExclusive: LocalDate,
    ): Flow<List<SportActivity>> {
        observedSpaceId = spaceId
        loadFailure?.let { return flow { throw it } }
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
    ): String {
        throwNextFailure()
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                throw SportRepositoryException(SportFailure.Unknown)
            }
        }
        addGate?.await()
        addedName = name
        addedType = type
        addedRoutineId = routineId
        addedSession = session
        return "new-1"
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

    val deleted = mutableListOf<String>()

    var deletedEvent: CalendarEvent? = null

    override suspend fun deleteActivity(spaceId: String, activityId: String): CalendarEvent? {
        throwNextFailure()
        deleted += activityId
        return deletedEvent
    }

    class Restored(val spaceId: String, val activity: SportActivity, val event: CalendarEvent?)

    val restored = mutableListOf<Restored>()

    override suspend fun restoreActivity(spaceId: String, activity: SportActivity, event: CalendarEvent?) {
        throwNextFailure()
        restored += Restored(spaceId, activity, event)
    }

    val undoneAdds = mutableListOf<Pair<String, String>>()

    override suspend fun undoAddActivity(spaceId: String, activityId: String) {
        throwNextFailure()
        undoneAdds += spaceId to activityId
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
        sourceRoutineId: String?,
    ): String = "new-1"

    override suspend fun updateRoutine(
        routineId: String,
        title: String,
        description: String?,
        exercises: List<RoutineExercise>,
    ) = Unit

    override suspend fun deleteRoutine(routineId: String) = Unit

    override suspend fun restoreRoutine(routine: Routine) = Unit
}

private class FakeSportExerciseRepository : ExerciseRepository {
    val catalogState = MutableStateFlow<List<CatalogExercise>>(emptyList())

    var catalogCalls = 0

    override fun catalog(): Flow<List<CatalogExercise>> {
        catalogCalls++
        return catalogState
    }
    override fun isCurrentUserCatalogAdmin(): Boolean = false
    override fun currentUserId(): String? = "u"
    override suspend fun exerciseById(id: String): CatalogExercise? = null
    override suspend fun createExercise(input: ExerciseInput): String = "new-1"
    override suspend fun updateExercise(id: String, input: ExerciseInput) = Unit
    override suspend fun deleteExercise(id: String) = Unit

    override suspend fun restoreExercise(exercise: CatalogExercise) = Unit
}

private fun catalogExercise(id: String, name: String) = CatalogExercise(
    id = id,
    name = name,
    type = ExerciseType.PESO_LIBRE,
    muscleGroup = null,
    ownerUid = "u",
    createdBy = "u",
    createdByName = "U",
    createdAt = Instant.EPOCH,
    updatedBy = "u",
    updatedAt = Instant.EPOCH,
)

private fun routine(id: String, title: String, visibility: RoutineVisibility = RoutineVisibility.PRIVATE) = Routine(
    id = id,
    ownerUid = "u",
    visibility = visibility,
    title = title,
    description = null,
    exercises = null,
    createdBy = "u",
    createdByName = "U",
    createdAt = Instant.EPOCH,
    updatedBy = "u",
    updatedAt = Instant.EPOCH,
)

private fun linkedEvent(id: String, title: String) = CalendarEvent(
    id = id,
    title = title,
    description = null,
    allDay = true,
    startAt = Instant.EPOCH,
    endAt = Instant.EPOCH.plusSeconds(86_400),
    startDate = LocalDate.of(1970, 1, 1),
    endDateExclusive = LocalDate.of(1970, 1, 2),
    timeZone = "UTC",
    color = EventColor.BLUE,
    participantIds = emptyList(),
    participantNames = emptyList(),
    createdBy = "owner",
    createdByName = "David",
    createdAt = Instant.EPOCH,
    updatedBy = "owner",
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
