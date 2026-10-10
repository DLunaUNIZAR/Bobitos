package com.dlunaunizar.bobitos.feature.routines

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseInput
import com.dlunaunizar.bobitos.core.model.ExerciseSet
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.Routine
import com.dlunaunizar.bobitos.core.model.RoutineExercise
import com.dlunaunizar.bobitos.core.model.RoutineVisibility
import com.dlunaunizar.bobitos.data.repository.ExerciseRepository
import com.dlunaunizar.bobitos.data.repository.RoutineFailure
import com.dlunaunizar.bobitos.data.repository.RoutineRepository
import com.dlunaunizar.bobitos.data.repository.RoutineRepositoryException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RoutinesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRoutineRepository()
    private val exerciseRepository = FakeExerciseRepository()
    private val viewModel = RoutinesViewModel(repository, exerciseRepository)

    @Test
    fun `observes global and personal routines without touching the exercise catalog`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.globalState.value = listOf(routine("g1", RoutineVisibility.GLOBAL, "Full body"))
            repository.mineState.value = listOf(routine("m1", RoutineVisibility.PRIVATE, "Empuje"))

            viewModel.observe()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("Full body"), (state.global as UiState.Content).value.map(Routine::title))
            assertEquals(listOf("Empuje"), (state.mine as UiState.Content).value.map(Routine::title))
            assertEquals(0, exerciseRepository.catalogCalls)
        }

    @Test
    fun `does not subscribe to the exercise catalog until the editor needs it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            exerciseRepository.catalogState.value = listOf(exercise("press-banca", "Press banca"))

            viewModel.observe()
            advanceUntilIdle()
            assertEquals(0, exerciseRepository.catalogCalls)

            viewModel.observeExerciseCatalog()
            viewModel.observeExerciseCatalog()
            advanceUntilIdle()

            assertEquals(1, exerciseRepository.catalogCalls)
            assertEquals(listOf("Press banca"), viewModel.uiState.value.exercises.map(CatalogExercise::name))
        }

    @Test
    fun `observing the exercise catalog again after stopObserving resubscribes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            viewModel.observe()
            viewModel.observeExerciseCatalog()
            advanceUntilIdle()
            viewModel.stopObserving()

            viewModel.observe()
            viewModel.observeExerciseCatalog()
            advanceUntilIdle()

            assertEquals(2, exerciseRepository.catalogCalls)
        }

    @Test
    fun `setQuery updates the state`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.setQuery("empu")

        assertEquals("empu", viewModel.uiState.value.query)
    }

    @Test
    fun `creating a routine trims the title, keeps its exercises and reports success`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val exercises = listOf(
                RoutineExercise("Press banca", type = ExerciseType.PESO_LIBRE, sets = listOf(ExerciseSet(10, 60.0))),
                RoutineExercise("Cinta", type = ExerciseType.CARDIO, durationMinutes = 20, level = "5"),
            )
            viewModel.createRoutine(RoutineVisibility.PRIVATE, "  Empuje  ", "  ", exercises)
            advanceUntilIdle()

            assertEquals(1, repository.createCount)
            assertEquals(RoutineVisibility.PRIVATE, repository.lastVisibility)
            assertEquals("Empuje", repository.lastTitle)
            assertEquals(null, repository.lastDescription)
            assertEquals(exercises, repository.lastExercises)
            assertEquals(RoutineUiMessage.Saved, viewModel.uiState.value.notice)
        }

    @Test
    fun `an invalid routine never reaches the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.createRoutine(RoutineVisibility.PRIVATE, "   ", null, emptyList())

        assertEquals(0, repository.createCount)
        assertEquals(RoutineUiMessage.TitleRequired, viewModel.uiState.value.error)
    }

    @Test
    fun `an admin publishes the routine as GLOBAL`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.admin = true
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createRoutine(RoutineVisibility.GLOBAL, "Full body", null, emptyList())
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isAdmin)
        assertEquals(RoutineVisibility.GLOBAL, repository.lastVisibility)
    }

    @Test
    fun `updating a routine forwards its id and exercises`() = runTest(mainDispatcherRule.testDispatcher) {
        val exercises = listOf(RoutineExercise("Sentadilla", type = ExerciseType.PESO_LIBRE))
        viewModel.updateRoutine("r1", "Pierna", "notas", exercises)
        advanceUntilIdle()

        assertEquals("r1", repository.lastUpdatedId)
        assertEquals("Pierna", repository.lastTitle)
        assertEquals("notas", repository.lastDescription)
        assertEquals(exercises, repository.lastExercises)
        assertEquals(RoutineUiMessage.Saved, viewModel.uiState.value.notice)
    }

    @Test
    fun `deleting a routine reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.deleteRoutine("r1")
        advanceUntilIdle()

        assertEquals("r1", repository.lastDeletedId)
        assertEquals(RoutineUiMessage.Deleted, viewModel.uiState.value.notice)
    }

    @Test
    fun `a repository failure maps to its message`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.failure = RoutineFailure.RoutineNotFound

        viewModel.updateRoutine("gone", "Empuje", null, emptyList())
        advanceUntilIdle()

        assertEquals(RoutineUiMessage.NotFound, viewModel.uiState.value.error)
        assertEquals(false, viewModel.uiState.value.isSaving)
    }

    @Test
    fun `editor save is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate

        viewModel.createRoutine(RoutineVisibility.PRIVATE, "Empuje", null, emptyList())
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        assertTrue(viewModel.uiState.value.isSaving)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.SAVED, viewModel.uiState.value.editorSave)
        assertFalse(viewModel.uiState.value.isSaving)
        viewModel.consumeEditorSave()
        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
    }

    @Test
    fun `editor save failure or timeout leaves FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.failure = RoutineFailure.Network
        viewModel.createRoutine(RoutineVisibility.PRIVATE, "Empuje", null, emptyList())
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(RoutineUiMessage.NetworkError, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)

        repository.failure = null
        viewModel.consumeEditorSave()
        repository.hang = true
        viewModel.updateRoutine("r1", "Empuje", null, emptyList())
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(RoutineUiMessage.SaveTimeout, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `deleting does not touch the editor status`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.deleteRoutine("r1")
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
    }
}

private class FakeExerciseRepository : ExerciseRepository {
    val catalogState = MutableStateFlow<List<CatalogExercise>>(emptyList())
    var catalogCalls = 0

    override fun catalog(): Flow<List<CatalogExercise>> {
        catalogCalls++
        return catalogState
    }
    override fun isCurrentUserCatalogAdmin(): Boolean = false
    override fun currentUserId(): String? = "u"
    override suspend fun exerciseById(id: String): CatalogExercise? = null
    override suspend fun createExercise(input: ExerciseInput) = Unit
    override suspend fun updateExercise(id: String, input: ExerciseInput) = Unit
    override suspend fun deleteExercise(id: String) = Unit
}

private class FakeRoutineRepository : RoutineRepository {
    var addGate: CompletableDeferred<Unit>? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    val globalState = MutableStateFlow<List<Routine>>(emptyList())
    val mineState = MutableStateFlow<List<Routine>>(emptyList())
    var createCount = 0
    var lastVisibility: RoutineVisibility? = null
    var lastTitle: String? = null
    var lastDescription: String? = null
    var lastExercises: List<RoutineExercise> = emptyList()
    var lastUpdatedId: String? = null
    var lastDeletedId: String? = null
    var admin = false
    var failure: RoutineFailure? = null

    override fun globalRoutines(): Flow<List<Routine>> = globalState
    override fun myRoutines(): Flow<List<Routine>> = mineState
    override fun isCurrentUserRoutineAdmin(): Boolean = admin

    override suspend fun createRoutine(
        visibility: RoutineVisibility,
        title: String,
        description: String?,
        exercises: List<RoutineExercise>,
    ) {
        failure?.let { throw RoutineRepositoryException(it) }
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                throw RoutineRepositoryException(RoutineFailure.Unknown)
            }
        }
        addGate?.await()
        createCount++
        lastVisibility = visibility
        lastTitle = title
        lastDescription = description
        lastExercises = exercises
    }

    override suspend fun updateRoutine(
        routineId: String,
        title: String,
        description: String?,
        exercises: List<RoutineExercise>,
    ) {
        failure?.let { throw RoutineRepositoryException(it) }
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                throw RoutineRepositoryException(RoutineFailure.Unknown)
            }
        }
        addGate?.await()
        lastUpdatedId = routineId
        lastTitle = title
        lastDescription = description
        lastExercises = exercises
    }

    override suspend fun deleteRoutine(routineId: String) {
        failure?.let { throw RoutineRepositoryException(it) }
        lastDeletedId = routineId
    }
}

private fun routine(id: String, visibility: RoutineVisibility, title: String) = Routine(
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

private fun exercise(id: String, name: String) = CatalogExercise(
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
