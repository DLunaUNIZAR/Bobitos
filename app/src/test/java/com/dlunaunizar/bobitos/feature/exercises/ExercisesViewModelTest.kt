package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseEquipment
import com.dlunaunizar.bobitos.core.model.ExerciseInput
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.slug
import com.dlunaunizar.bobitos.data.repository.ExerciseFailure
import com.dlunaunizar.bobitos.data.repository.ExerciseRepository
import com.dlunaunizar.bobitos.data.repository.ExerciseRepositoryException
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ExercisesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeExerciseRepository()
    private val viewModel = ExercisesViewModel(repository)

    @Test
    fun `observes the catalog`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.catalogState.value = listOf(exercise("press-banca", "Press banca", ExerciseType.PESO_LIBRE))

        viewModel.observe()
        advanceUntilIdle()

        assertEquals(
            listOf("Press banca"),
            (viewModel.uiState.value.catalog as UiState.Content).value.map(CatalogExercise::name),
        )
    }

    @Test
    fun `creating trims the name and reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createExercise(input("  Sentadilla  ", ExerciseType.PESO_LIBRE))
        advanceUntilIdle()

        assertEquals("Sentadilla", repository.createdName)
        assertEquals(ExerciseType.PESO_LIBRE, repository.createdType)
        assertEquals(ExerciseUiMessage.Saved, viewModel.uiState.value.notice)
    }

    @Test
    fun `create passes the full input to the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()
        val full = ExerciseInput(
            name = " Remo ",
            type = ExerciseType.PESO_CORPORAL,
            muscleGroup = "Espalda",
            description = "Con control.",
            equipment = listOf(ExerciseEquipment.BARRA, ExerciseEquipment.BANCO),
        )

        viewModel.createExercise(full)
        advanceUntilIdle()

        assertEquals(full.copy(name = "Remo"), repository.createdInput)
    }

    @Test
    fun `DescriptionTooLong failure shows its message`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()
        repository.createFailure = ExerciseRepositoryException(ExerciseFailure.DescriptionTooLong)

        viewModel.createExercise(input("Remo", ExerciseType.MAQUINA))
        advanceUntilIdle()

        assertEquals(ExerciseUiMessage.DescriptionTooLong, viewModel.uiState.value.error)
    }

    @Test
    fun `creating a duplicate is rejected without reaching the repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.catalogState.value = listOf(exercise(slug("Press banca"), "Press banca", ExerciseType.MAQUINA))
            viewModel.observe()
            advanceUntilIdle()

            viewModel.createExercise(input("Press banca", ExerciseType.MAQUINA))

            assertNull(repository.createdName)
            assertEquals(ExerciseUiMessage.AlreadyExists, viewModel.uiState.value.error)
        }

    @Test
    fun `deleting delegates to the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()

        viewModel.deleteExercise("press-banca")
        advanceUntilIdle()

        assertEquals("press-banca", repository.deletedId)
        assertEquals(ExerciseUiMessage.Deleted, viewModel.uiState.value.notice)
    }

    @Test
    fun `editor save is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate

        viewModel.createExercise(input("Remo", ExerciseType.MAQUINA))
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
        repository.createFailure = ExerciseRepositoryException(ExerciseFailure.Network)
        viewModel.createExercise(input("Remo", ExerciseType.MAQUINA))
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(ExerciseUiMessage.NetworkError, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)

        repository.createFailure = null
        viewModel.consumeEditorSave()
        repository.hang = true
        viewModel.createExercise(input("Remo", ExerciseType.MAQUINA))
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.editorSave)
        advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(ExerciseUiMessage.SaveTimeout, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `AlreadyExists from the editor leaves FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.catalogState.value = listOf(exercise(slug("Press banca"), "Press banca", ExerciseType.MAQUINA))
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createExercise(input("Press banca", ExerciseType.MAQUINA))

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.editorSave)
        assertEquals(ExerciseUiMessage.AlreadyExists, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `deleting does not touch the editor status`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.deleteExercise("press-banca")
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.editorSave)
    }
}

private class FakeExerciseRepository : ExerciseRepository {
    var addGate: CompletableDeferred<Unit>? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    val catalogState = MutableStateFlow<List<CatalogExercise>>(emptyList())
    var createdInput: ExerciseInput? = null
    var createFailure: Throwable? = null
    val createdName: String? get() = createdInput?.name
    val createdType: ExerciseType? get() = createdInput?.type
    var deletedId: String? = null

    override fun catalog(): Flow<List<CatalogExercise>> = catalogState
    override fun isCurrentUserCatalogAdmin(): Boolean = false
    override fun currentUserId(): String? = "me"
    override suspend fun exerciseById(id: String): CatalogExercise? = catalogState.value.firstOrNull { it.id == id }

    override suspend fun createExercise(input: ExerciseInput) {
        createFailure?.let { throw it }
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                throw ExerciseRepositoryException(ExerciseFailure.Unknown)
            }
        }
        addGate?.await()
        createdInput = input
    }

    override suspend fun updateExercise(id: String, input: ExerciseInput) = Unit

    override suspend fun deleteExercise(id: String) {
        deletedId = id
    }
}

private fun input(name: String, type: ExerciseType) = ExerciseInput(name, type, null, null, emptyList())

private fun exercise(id: String, name: String, type: ExerciseType) = CatalogExercise(
    id = id,
    name = name,
    type = type,
    muscleGroup = null,
    ownerUid = "me",
    createdBy = "me",
    createdByName = "Yo",
    createdAt = Instant.EPOCH,
    updatedBy = "me",
    updatedAt = Instant.EPOCH,
)
