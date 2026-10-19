package com.dlunaunizar.bobitos.feature.exercises

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CatalogExercise
import com.dlunaunizar.bobitos.core.model.ExerciseEquipment
import com.dlunaunizar.bobitos.core.model.ExerciseImage
import com.dlunaunizar.bobitos.core.model.ExerciseInput
import com.dlunaunizar.bobitos.core.model.ExerciseSource
import com.dlunaunizar.bobitos.core.model.ExerciseType
import com.dlunaunizar.bobitos.core.model.slug
import com.dlunaunizar.bobitos.data.repository.ExerciseFailure
import com.dlunaunizar.bobitos.data.repository.ExerciseImageRepository
import com.dlunaunizar.bobitos.data.repository.ExerciseRepository
import com.dlunaunizar.bobitos.data.repository.ExerciseRepositoryException
import com.dlunaunizar.bobitos.data.repository.LoadedImage
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
    private val imageRepository = FakeImageRepository()
    private val viewModel by lazy { ExercisesViewModel(repository, imageRepository) }

    @Test
    fun `loadImage delegates to the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        val withImage = exercise("press-banca", "Press banca", ExerciseType.PESO_LIBRE)
            .copy(image = ExerciseImage("a".repeat(64), null, "CC-BY-SA-4.0", null))
        val loaded = viewModel.loadImage(withImage)!!
        assertEquals(listOf<Byte>(7), loaded.bytes.toList())
        assertEquals("Ana", loaded.author)
        assertEquals("CC0-1.0", loaded.license)
        assertEquals(listOf("press-banca" to "a".repeat(64)), imageRepository.requests)

        val without = exercise("sentadilla", "Sentadilla", ExerciseType.PESO_LIBRE)
        assertNull(viewModel.loadImage(without))
        assertEquals(1, imageRepository.requests.size)
    }

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
    fun `an offline catalog shows a localized message instead of the exception text`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.catalogFailure = java.io.IOException("Failed to get document because the client is offline.")

            viewModel.observe()
            advanceUntilIdle()

            assertEquals(UiState.Error(), viewModel.uiState.value.catalog)
            assertEquals(ExerciseUiMessage.CatalogUnavailable, viewModel.uiState.value.catalogError)
        }

    @Test
    fun `retrying the catalog subscribes again and shows the list when it answers`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.catalogFailure = java.io.IOException("offline")
            viewModel.observe()
            advanceUntilIdle()

            repository.catalogFailure = null
            repository.catalogState.value = listOf(exercise("press-banca", "Press banca", ExerciseType.PESO_LIBRE))
            viewModel.retryCatalog()
            advanceUntilIdle()

            assertEquals(
                listOf("Press banca"),
                (viewModel.uiState.value.catalog as UiState.Content).value.map(CatalogExercise::name),
            )
            assertNull(viewModel.uiState.value.catalogError)
        }

    @Test
    fun `creating trims the name and reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createExercise(input("  Sentadilla  ", ExerciseType.PESO_LIBRE))
        advanceUntilIdle()

        assertEquals("Sentadilla", repository.createdName)
        assertEquals(ExerciseType.PESO_LIBRE, repository.createdType)
        assertNull(viewModel.uiState.value.save.notice)
    }

    @Test
    fun creatingOneItemOffersUndoWithItsIdAndName() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createExercise(input("  Sentadilla  ", ExerciseType.PESO_LIBRE))
        advanceUntilIdle()

        assertEquals(CreatedItem("sentadilla", "Sentadilla"), viewModel.uiState.value.save.created)
        assertNull(viewModel.uiState.value.save.notice)

        viewModel.feedback.consumeCreated()
        assertNull(viewModel.uiState.value.save.created)
    }

    @Test
    fun undoCreateDeletesExactlyThatItem() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.undoCreate("sentadilla")
        advanceUntilIdle()

        assertEquals("sentadilla", repository.deletedId)
        assertNull(viewModel.uiState.value.save.notice)
    }

    @Test
    fun editingDoesNotOfferUndo() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.updateExercise("sentadilla", input("Sentadilla", ExerciseType.PESO_LIBRE))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.save.created)
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

        assertEquals(ExerciseUiMessage.DescriptionTooLong, viewModel.uiState.value.save.editorError)
    }

    @Test
    fun `creating a duplicate is rejected without reaching the repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.catalogState.value = listOf(exercise(slug("Press banca"), "Press banca", ExerciseType.MAQUINA))
            viewModel.observe()
            advanceUntilIdle()

            viewModel.createExercise(input("Press banca", ExerciseType.MAQUINA))

            assertNull(repository.createdName)
            assertEquals(ExerciseUiMessage.AlreadyExists, viewModel.uiState.value.save.editorError)
        }

    @Test
    fun `deleting delegates to the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe()
        advanceUntilIdle()

        viewModel.deleteExercise("press-banca")
        advanceUntilIdle()

        assertEquals("press-banca", repository.deletedId)
        assertNull(viewModel.uiState.value.save.notice)
    }

    @Test
    fun `deleting a common one that cannot be undone reports it`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.deleteExercise("press-banca", undoable = false)
        advanceUntilIdle()

        assertEquals(ExerciseUiMessage.Deleted, viewModel.uiState.value.save.notice)
    }

    @Test
    fun `deleting an undoable one has no notice of its own`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.deleteExercise("press-banca", undoable = true)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.save.notice)
    }

    @Test
    fun `canUndoDelete only for the own exercises without source or image`() {
        val own = exercise("press-banca", "Press banca", ExerciseType.PESO_LIBRE)
        assertTrue(own.canUndoDelete("me"))
        assertFalse(own.copy(source = ExerciseSource("wger", 1L, "CC-BY-SA-4.0", null, null)).canUndoDelete("me"))
        assertFalse(own.copy(image = ExerciseImage("a".repeat(64), null, "CC-BY-SA-4.0", null)).canUndoDelete("me"))
        // Aunque borre el admin, uno de otro dueño no se devuelve.
        assertFalse(own.copy(ownerUid = "other").canUndoDelete("me"))
        assertFalse(own.canUndoDelete(null))
    }

    @Test
    fun `undoing a delete restores the exercise with its id`() = runTest(mainDispatcherRule.testDispatcher) {
        val deleted = exercise("press-banca-2", "Press banca", ExerciseType.PESO_LIBRE)

        viewModel.restoreExercise(deleted)
        advanceUntilIdle()

        assertEquals(deleted, repository.restored)
        assertEquals("press-banca-2", repository.restored?.id)
        assertNull(viewModel.uiState.value.save.error)
    }

    @Test
    fun `a failed undo shows the error`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.restoreFailure = ExerciseRepositoryException(ExerciseFailure.Network)

        viewModel.restoreExercise(exercise("press-banca", "Press banca", ExerciseType.PESO_LIBRE))
        advanceUntilIdle()

        assertEquals(ExerciseUiMessage.NetworkError, viewModel.uiState.value.save.error)
    }

    @Test
    fun `onDeleted runs only when the delete finished well`() = runTest(mainDispatcherRule.testDispatcher) {
        var deletedCalls = 0
        repository.deleteFailure = ExerciseRepositoryException(ExerciseFailure.Network)
        viewModel.deleteExercise("press-banca") { deletedCalls++ }
        advanceUntilIdle()
        assertEquals(0, deletedCalls)
        assertEquals(ExerciseUiMessage.NetworkError, viewModel.uiState.value.save.error)

        repository.deleteFailure = null
        viewModel.deleteExercise("press-banca") { deletedCalls++ }
        advanceUntilIdle()
        assertEquals(1, deletedCalls)
    }

    @Test
    fun `editor save is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate

        viewModel.createExercise(input("Remo", ExerciseType.MAQUINA))
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
        repository.createFailure = ExerciseRepositoryException(ExerciseFailure.Network)
        viewModel.createExercise(input("Remo", ExerciseType.MAQUINA))
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        assertEquals(ExerciseUiMessage.NetworkError, viewModel.uiState.value.save.editorError)
        assertFalse(viewModel.uiState.value.save.isSaving)

        repository.createFailure = null
        viewModel.feedback.resetEditor()
        repository.hang = true
        viewModel.createExercise(input("Remo", ExerciseType.MAQUINA))
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.save.editor)
        advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        assertEquals(ExerciseUiMessage.SaveTimeout, viewModel.uiState.value.save.editorError)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `AlreadyExists from the editor leaves FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.catalogState.value = listOf(exercise(slug("Press banca"), "Press banca", ExerciseType.MAQUINA))
        viewModel.observe()
        advanceUntilIdle()

        viewModel.createExercise(input("Press banca", ExerciseType.MAQUINA))

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        assertEquals(ExerciseUiMessage.AlreadyExists, viewModel.uiState.value.save.editorError)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }

    @Test
    fun `deleting does not touch the editor status`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.deleteExercise("press-banca")
        advanceUntilIdle()

        assertEquals(EditorSaveStatus.IDLE, viewModel.uiState.value.save.editor)
    }

    @Test
    fun `a forced restore that ends first keeps saving on until the other write ends`() =
        runTest(mainDispatcherRule.testDispatcher) {
            repository.addGate = CompletableDeferred()
            viewModel.createExercise(input("Remo", ExerciseType.MAQUINA))
            runCurrent() // sin avanzar el reloj: el alta del editor tiene tiempo máximo

            viewModel.restoreExercise(exercise("press-banca", "Press banca", ExerciseType.PESO_LIBRE))
            runCurrent()

            assertEquals("press-banca", repository.restored?.id)
            assertTrue(viewModel.uiState.value.save.isSaving)

            repository.addGate?.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.save.isSaving)
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
    var deleteFailure: Throwable? = null
    var restored: CatalogExercise? = null
    var restoreFailure: Throwable? = null

    var catalogFailure: Throwable? = null

    override fun catalog(): Flow<List<CatalogExercise>> =
        catalogFailure?.let { failure -> kotlinx.coroutines.flow.flow { throw failure } } ?: catalogState
    override fun isCurrentUserCatalogAdmin(): Boolean = false
    override fun currentUserId(): String? = "me"
    override suspend fun exerciseById(id: String): CatalogExercise? = catalogState.value.firstOrNull { it.id == id }

    override suspend fun createExercise(input: ExerciseInput): String {
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
        return slug(input.name)
    }

    override suspend fun updateExercise(id: String, input: ExerciseInput) = Unit

    override suspend fun deleteExercise(id: String) {
        deleteFailure?.let { throw it }
        deletedId = id
    }

    override suspend fun restoreExercise(exercise: CatalogExercise) {
        restoreFailure?.let { throw it }
        restored = exercise
    }
}

private fun input(name: String, type: ExerciseType) = ExerciseInput(name, type, null, null, emptyList())

private class FakeImageRepository : ExerciseImageRepository {
    val requests = mutableListOf<Pair<String, String>>()
    var image: LoadedImage? = LoadedImage(byteArrayOf(7), "Ana", "CC0-1.0", "https://wger.de/media/a.png")

    override suspend fun image(exerciseId: String, hash: String): LoadedImage? {
        requests += exerciseId to hash
        return image
    }
}

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
