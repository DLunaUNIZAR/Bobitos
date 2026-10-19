package com.dlunaunizar.bobitos.feature.notes

import com.dlunaunizar.bobitos.MainDispatcherRule
import com.dlunaunizar.bobitos.core.common.CreatedItem
import com.dlunaunizar.bobitos.core.common.EDITOR_SAVE_TIMEOUT_MILLIS
import com.dlunaunizar.bobitos.core.common.EditorSaveStatus
import com.dlunaunizar.bobitos.core.common.FailureKind
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.common.Undoable
import com.dlunaunizar.bobitos.core.model.Note
import com.dlunaunizar.bobitos.data.repository.NoteFailure
import com.dlunaunizar.bobitos.data.repository.NoteRepository
import com.dlunaunizar.bobitos.data.repository.NoteRepositoryException
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
class NotesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeNoteRepository()
    private val viewModel by lazy { NotesViewModel(repository) }

    @Test
    fun `observes notes for the space`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.notesState.value = listOf(note("n1", "Wifi"))

        viewModel.observe("home")
        advanceUntilIdle()

        assertEquals(
            listOf("Wifi"),
            (viewModel.uiState.value.notes as UiState.Content).value.map(Note::title),
        )
    }

    @Test
    fun `adding a note trims the title and reports success`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        viewModel.addNote("  Wifi  ", "clave: 1234")
        advanceUntilIdle()

        assertEquals("Wifi", repository.addedTitle)
        assertNull(viewModel.uiState.value.save.notice)
    }

    @Test
    fun creatingOneItemOffersUndoWithItsIdAndName() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        viewModel.addNote("Wifi", null)
        runCurrent()

        val offer = viewModel.undoOffers.value.single()
        assertEquals(Undoable.Created("home", CreatedItem("new-1", "Wifi")), offer.undoable)
        assertNull(viewModel.uiState.value.save.notice)

        viewModel.undoControls.consumeOffer(offer.serial)
        assertTrue(viewModel.undoOffers.value.isEmpty())
    }

    @Test
    fun `undoing an add goes through undoAddNote`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        viewModel.undo(Undoable.Created("space-a", CreatedItem("new-1", "Wifi")))
        advanceUntilIdle()

        assertEquals(listOf("space-a" to "new-1"), repository.undoneAdds)
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun `an add another member changed is not undone and says so`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.nextFailure = NoteRepositoryException(NoteFailure.NoteChanged)
        viewModel.undo(Undoable.Created("home", CreatedItem("new-1", "Wifi")))
        advanceUntilIdle()

        assertEquals(NoteUiMessage.NoteChanged, viewModel.uiState.value.save.error)
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun editingDoesNotOfferUndo() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        viewModel.updateNote("n1", "Wifi", null)
        advanceUntilIdle()

        assertTrue(viewModel.undoOffers.value.isEmpty())
    }

    @Test
    fun `an invalid note never reaches the repository`() {
        viewModel.observe("home")
        viewModel.addNote("   ", null)

        assertNull(repository.addedTitle)
        assertEquals(NoteUiMessage.TitleRequired, viewModel.uiState.value.save.editorError)
        assertNull(viewModel.uiState.value.save.error)
    }

    @Test
    fun `toggling pin delegates to the repository`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        viewModel.setPinned("n1", true)
        advanceUntilIdle()

        assertEquals("n1" to true, repository.pinnedChange)
        assertEquals(NoteUiMessage.NotePinned, viewModel.uiState.value.save.notice)
    }

    @Test
    fun aLoadFailureNeverExposesTheExceptionText() = runTest(mainDispatcherRule.testDispatcher) {
        repository.loadFailure = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")

        viewModel.observe("home")
        advanceUntilIdle()

        assertEquals(UiState.Error(FailureKind.UNKNOWN), viewModel.uiState.value.notes)
    }

    @Test
    fun `network failure is shown explicitly`() = runTest(mainDispatcherRule.testDispatcher) {
        repository.nextFailure = NoteRepositoryException(NoteFailure.Network)

        viewModel.observe("home")
        viewModel.deleteNote(note("n1", "Wifi"))
        advanceUntilIdle()

        assertEquals(NoteUiMessage.NetworkError, viewModel.uiState.value.save.error)
    }

    @Test
    fun `editor save is SAVED only after the repository answers`() = runTest(mainDispatcherRule.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate
        viewModel.observe("home")

        viewModel.addNote("Wifi", null)
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
    fun `undo is offered only once the delete has finished`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        repository.nextFailure = NoteRepositoryException(NoteFailure.Network)
        viewModel.deleteNote(note("n1", "Wifi"))
        runCurrent()
        assertTrue(viewModel.undoOffers.value.isEmpty())
        assertEquals(NoteUiMessage.NetworkError, viewModel.uiState.value.save.error)

        repository.nextFailure = null
        viewModel.deleteNote(note("n1", "Wifi"))
        runCurrent()
        assertEquals(1, viewModel.undoOffers.value.size)
        assertNull(viewModel.uiState.value.save.notice)
        assertEquals(listOf("n1"), repository.deleted)
    }

    @Test
    fun `deleting offers undo with the note and its space`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("space-a")
        val wifi = note("n1", "Wifi").copy(pinned = true)
        viewModel.deleteNote(wifi)
        runCurrent()

        assertEquals(Undoable.Deleted("space-a", wifi), viewModel.undoOffers.value.single().undoable)
    }

    @Test
    fun `undo of a delete goes to the space where it was deleted`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("space-a")
        viewModel.deleteNote(note("n1", "Wifi"))
        runCurrent()
        val offered = viewModel.undoOffers.value.single().undoable
        viewModel.observe("space-b")

        viewModel.undo(offered)
        advanceUntilIdle()

        assertEquals("space-a", repository.restored.single().first)
    }

    @Test
    fun `undoing a delete brings the note back with its id and pin`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        viewModel.undo(Undoable.Deleted("space-a", note("n1", "Wifi").copy(pinned = true)))
        advanceUntilIdle()

        val (space, restored) = repository.restored.single()
        assertEquals("space-a", space)
        assertEquals("n1", restored.id)
        assertTrue(restored.pinned)
    }

    @Test
    fun `undo writes to the note's own space`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("space-a")
        viewModel.observe("space-b")
        viewModel.undo(Undoable.Deleted("space-a", note("n1", "Wifi")))
        advanceUntilIdle()

        assertEquals("space-a", repository.restored.single().first)
    }

    @Test
    fun `a failed undo surfaces the error`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        repository.nextFailure = NoteRepositoryException(NoteFailure.SpaceNotFound)
        viewModel.undo(Undoable.Deleted("home", note("n1", "Wifi")))
        advanceUntilIdle()

        assertEquals(NoteUiMessage.SpaceNotFound, viewModel.uiState.value.save.error)
    }

    @Test
    fun `undo runs while another write is in flight and saving stays on until both end`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val addGate = CompletableDeferred<Unit>()
            val restoreGate = CompletableDeferred<Unit>()
            repository.addGate = addGate
            repository.restoreGate = restoreGate
            viewModel.observe("home")

            viewModel.addNote("Wifi", null)
            viewModel.undo(Undoable.Deleted("home", note("n1", "Luz")))
            runCurrent()
            assertTrue(viewModel.uiState.value.save.isSaving)

            restoreGate.complete(Unit)
            runCurrent() // sin avanzar el reloj: el alta del editor tiene tiempo máximo
            assertEquals("n1", repository.restored.single().second.id)
            assertTrue(viewModel.uiState.value.save.isSaving)

            addGate.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.save.isSaving)
        }

    @Test
    fun `editor save failure or timeout leaves FAILED`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel.observe("home")
        repository.nextFailure = NoteRepositoryException(NoteFailure.Network)
        viewModel.addNote("Wifi", null)
        advanceUntilIdle()
        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        assertEquals(NoteUiMessage.NetworkError, viewModel.uiState.value.save.editorError)
        assertNull(viewModel.uiState.value.save.error)
        assertFalse(viewModel.uiState.value.save.isSaving)

        repository.nextFailure = null
        viewModel.feedback.resetEditor()
        repository.hang = true
        viewModel.addNote("Wifi", null)
        assertEquals(EditorSaveStatus.SAVING, viewModel.uiState.value.save.editor)
        advanceTimeBy(EDITOR_SAVE_TIMEOUT_MILLIS + 1)
        runCurrent()

        assertEquals(EditorSaveStatus.FAILED, viewModel.uiState.value.save.editor)
        assertEquals(NoteUiMessage.SaveTimeout, viewModel.uiState.value.save.editorError)
        assertFalse(viewModel.uiState.value.save.isSaving)
    }
}

private class FakeNoteRepository : NoteRepository {
    val restored = mutableListOf<Pair<String, Note>>()

    var addGate: CompletableDeferred<Unit>? = null
    var restoreGate: CompletableDeferred<Unit>? = null

    // Nunca responde; como los repositorios reales, convierte la cancelación en otra excepción.
    var hang = false

    val notesState = MutableStateFlow<List<Note>>(emptyList())
    var addedTitle: String? = null
    val deleted = mutableListOf<String>()
    var pinnedChange: Pair<String, Boolean>? = null
    var nextFailure: NoteRepositoryException? = null
    var loadFailure: Throwable? = null

    override fun notes(spaceId: String): Flow<List<Note>> =
        loadFailure?.let { error -> flow { throw error } } ?: notesState

    override suspend fun addNote(spaceId: String, title: String, body: String?): String {
        throwNextFailure()
        if (hang) {
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                throw NoteRepositoryException(NoteFailure.Unknown)
            }
        }
        addGate?.await()
        addedTitle = title
        return "new-1"
    }

    override suspend fun updateNote(spaceId: String, noteId: String, title: String, body: String?) {
        throwNextFailure()
    }

    override suspend fun setPinned(spaceId: String, noteId: String, pinned: Boolean) {
        throwNextFailure()
        pinnedChange = noteId to pinned
    }

    override suspend fun deleteNote(spaceId: String, noteId: String) {
        throwNextFailure()
        deleted += noteId
    }

    val undoneAdds = mutableListOf<Pair<String, String>>()

    override suspend fun undoAddNote(spaceId: String, noteId: String) {
        throwNextFailure()
        undoneAdds += spaceId to noteId
    }

    override suspend fun restoreNote(spaceId: String, note: Note) {
        restoreGate?.await()
        throwNextFailure()
        restored += spaceId to note
    }

    private fun throwNextFailure() {
        nextFailure?.let { throw it }
    }
}

private fun note(id: String, title: String) = Note(
    id = id,
    title = title,
    body = null,
    pinned = false,
    createdBy = "owner",
    createdByName = "David",
    createdAt = Instant.EPOCH,
    updatedBy = "owner",
    updatedAt = Instant.EPOCH,
)
